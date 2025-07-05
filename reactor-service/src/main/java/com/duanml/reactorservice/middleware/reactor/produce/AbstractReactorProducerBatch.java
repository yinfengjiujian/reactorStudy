package com.duanml.reactorservice.middleware.reactor.produce;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批量生产者基类，支持基于 Redis Set/BloomFilter 的去重入队。
 *
 * 用法说明：
 * 1. 子类需实现 fetchBatch、toJson、getPrimaryKey 三个抽象方法；
 * 2. 可以选择去重方式（不去重、Redis Set/BloomFilter）；
 * 3. 支持限流（队列长度超过 queueMaxLength 时暂停投递）；
 * 4. 支持优雅停止生产线程。
 *
 * @param <T> 任务数据类型
 */
@Slf4j
public abstract class AbstractReactorProducerBatch<T> {

    /**
     * 去重类型枚举
     */
    public enum DedupType {
        NONE,   // 不进行去重
        SET,    // 用Redis Set去重（精确、适合百万级以内主键）
        BLOOM   // 用RedisBloom布隆过滤器去重（适合千万/亿级主键，少量误判）
    }

    protected final StringRedisTemplate redisTemplate; // Redis操作模板
    protected final String queueKey;                   // 队列在Redis里的key
    protected final int queueMaxLength;                // 队列最大长度（限流用，超过暂停生产）
    protected final int pageSize;                      // 每次批量生产的数量
    protected final DedupType dedupType;               // 去重方式
    protected final String dedupKey;                   // 去重集合的key（Set或Bloom Filter）

    private final AtomicBoolean running = new AtomicBoolean(false);         // 是否正在生产
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);   // 是否请求停止
    private Thread workerThread;                                            // 生产线程

    /**
     * 构造方法
     * @param redisTemplate Redis操作对象
     * @param queueKey      队列key
     * @param queueMaxLength 队列最大长度
     * @param pageSize      批量查询/入队每页数量
     * @param dedupType     去重方式（NONE/SET/BLOOM）
     * @param dedupKey      去重集合key
     */
    protected AbstractReactorProducerBatch(StringRedisTemplate redisTemplate, String queueKey,
                                           int queueMaxLength, int pageSize,
                                           DedupType dedupType, String dedupKey) {
        this.redisTemplate = redisTemplate;
        this.queueKey = queueKey;
        this.queueMaxLength = queueMaxLength;
        this.pageSize = pageSize;
        this.dedupType = dedupType;
        this.dedupKey = dedupKey;
    }

    /**
     * 启动生产（异步线程，不阻塞主线程）
     */
    public synchronized void startProduce() {
        if (running.get()) {
            log.warn("Producer is already running.");
            return;
        }
        stopRequested.set(false);
        running.set(true);
        // 启动生产线程
        workerThread = new Thread(this::produceTasks, "BatchProducerWorker-" + queueKey);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * 生产主循环：分页查库、判断队列长度、判重、入队
     */
    private void produceTasks() {
        try {
            int page = 0;
            while (!stopRequested.get()) {
                // 1. 队列长度限流，队列太长暂停生产
                Long queueLen = redisTemplate.opsForList().size(queueKey);
                if (queueLen != null && queueLen > queueMaxLength) {
                    Thread.sleep(5000);
                    continue;
                }

                // 2. 分页查库（由子类实现）
                List<T> batch = fetchBatch(page * pageSize, pageSize);
                if (batch == null || batch.isEmpty()) {
                    log.info("No more tasks to produce, exiting.");
                    break; // 没有更多任务，退出循环
                }

                // 3. 批量入队，入队前先判重
                int realPush = 0; // 本批实际入队数量
                for (T task : batch) {
                    if (stopRequested.get()) {
                        log.warn("Stop requested, exiting producer thread.");
                        return; // 若请求停止立即退出
                    }
                    String primaryKey = getPrimaryKey(task); // 获取主键（唯一标识）
                    // 先查去重集合，未出现过才入队
                    if (!shouldPush(primaryKey)) {
                        continue;
                    }
                    // 入队
                    redisTemplate.opsForList().rightPush(queueKey, toJson(task));
                    // 记录到去重集合
                    markPushed(primaryKey);
                    realPush++;
                }
                log.info("Page {} produced {} new tasks (dedup type: {}).", page, realPush, dedupType);
                page++;
            }
        } catch (Exception e) {
            log.error("Failed to produce batch.", e);
        } finally {
            running.set(false);
            stopRequested.set(false);
        }
    }

    /**
     * 判断主键id是否已入队（去重核心逻辑）
     * @param id 主键（唯一标识）
     * @return true=未入队（可以入队），false=已入队（不可重复入队）
     */
    protected boolean shouldPush(String id) {
        switch (dedupType) {
            case SET:
                // Redis Set精确去重
                Boolean exists = redisTemplate.opsForSet().isMember(dedupKey, id);
                return exists == null || !exists;
            case BLOOM:
                // RedisBloom布隆过滤器（需要RedisBloom插件）
                Long existLong = redisTemplate.execute(
                        new DefaultRedisScript<>("return redis.call('BF.EXISTS', KEYS[1], ARGV[1])", Long.class),
                        Collections.singletonList(dedupKey), id);
                return existLong != null && existLong == 0; // 0代表没出现过
            default:
                return true; // 不去重，全部入队
        }
    }

    /**
     * 将主键id记录到去重集合
     * @param id 主键（唯一标识）
     */
    protected void markPushed(String id) {
        switch (dedupType) {
            case SET:
                redisTemplate.opsForSet().add(dedupKey, id);
                break;
            case BLOOM:
                redisTemplate.execute(
                        new DefaultRedisScript<>("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class),
                        Collections.singletonList(dedupKey), id);
                break;
            default:
                break;
        }
    }

    /**
     * 请求停止生产线程（线程会优雅退出）
     */
    public void requestStop() {
        stopRequested.set(true);
    }

    /**
     * 查询当前生产状态
     * @return ProducerStatus对象，包含是否运行、是否stopping、队列长度
     */
    public ProducerStatus status() {
        boolean isRunning = running.get();
        boolean isStopping = stopRequested.get();
        Long queueLen = redisTemplate.opsForList().size(queueKey);
        return new ProducerStatus(isRunning, isStopping, queueLen != null ? queueLen : 0L);
    }

    /**
     * 生产端状态结构体
     */
    public static class ProducerStatus {
        public final boolean running;  // 是否正在运行
        public final boolean stopping; // 是否正在停止
        public final long queueLen;    // 队列长度

        public ProducerStatus(boolean running, boolean stopping, long queueLen) {
            this.running = running;
            this.stopping = stopping;
            this.queueLen = queueLen;
        }
    }

    // ===================== 子类需实现的方法 =====================

    /**
     * 分页查库，返回一批任务
     * @param offset 偏移量
     * @param limit 批大小
     * @return 任务列表
     */
    protected abstract List<T> fetchBatch(int offset, int limit);

    /**
     * 任务对象转JSON字符串（建议用fastjson/gson等）
     * @param task 任务对象
     * @return JSON字符串
     */
    protected abstract String toJson(T task);

    /**
     * 获取任务对象唯一主键（如ID），用于去重
     * @param task 任务对象
     * @return 主键字符串
     */
    protected abstract String getPrimaryKey(T task);
}
