package com.duanml.reactorservice.middleware.reactor.produce;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>Title: com.duanml.reactorstudy.middleware.reactor.produce</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/3 20:15
 * Description: 批量生产者基类
 */
@Slf4j
public abstract class AbstractReactorProducerBatch<T> {

    protected final StringRedisTemplate redisTemplate;
    protected final String queueKey;
    protected final int queueMaxLength;
    protected final int pageSize;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private Thread workerThread;

    public AbstractReactorProducerBatch(StringRedisTemplate redisTemplate, String queueKey,
                                        int queueMaxLength, int pageSize) {
        this.redisTemplate = redisTemplate;
        this.queueKey = queueKey;
        this.queueMaxLength = queueMaxLength;
        this.pageSize = pageSize;
    }

    // 启动生产（非阻塞）
    public synchronized void startProduce() {
        if (running.get()) {
            log.warn("Producer is already running.");
            return;
        }
        stopRequested.set(false);
        running.set(true);
        workerThread = new Thread(this::produceTasks, "BatchProducerWorker-" + queueKey);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // 生产主循环
    private void produceTasks() {
        try {
            int page = 0;
            while (!stopRequested.get()) {
                // 队列限流
                Long queueLen = redisTemplate.opsForList().size(queueKey);
                if (queueLen != null && queueLen > queueMaxLength) {
                    Thread.sleep(5000);
                    continue;
                }

                // 分页查库（由子类实现）
                List<T> batch = fetchBatch(page * pageSize, pageSize);
                if (batch == null || batch.isEmpty()) {
                    log.warn("No more tasks to produce, exiting.");
                    break; // 没有更多任务，退出循环
                }

                // 将任务批量入队
                for (T task : batch) {
                    if (stopRequested.get()) {
                        log.warn("Stop requested, exiting producer thread.");
                        return; // 如果请求停止，立即退出
                    }
                    redisTemplate.opsForList().rightPush(queueKey, toJson(task));
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Failed to produce batch.", e);
            e.printStackTrace();
        } finally {
            running.set(false);
            stopRequested.set(false);
        }
    }

    // 请求停止
    public void requestStop() {
        stopRequested.set(true);
    }

    // 状态查询
    public ProducerStatus status() {
        boolean isRunning = running.get();
        boolean isStopping = stopRequested.get();
        Long queueLen = redisTemplate.opsForList().size(queueKey);
        return new ProducerStatus(isRunning, isStopping, queueLen != null ? queueLen : 0L);
    }

    // 状态结构体
    public static class ProducerStatus {
        public final boolean running;
        public final boolean stopping;
        public final long queueLen;

        public ProducerStatus(boolean running, boolean stopping, long queueLen) {
            this.running = running;
            this.stopping = stopping;
            this.queueLen = queueLen;
        }
    }

    // ======= 需要子类实现的方法 =======
    protected abstract List<T> fetchBatch(int offset, int limit);

    protected abstract String toJson(T task);

}
