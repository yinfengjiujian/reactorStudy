package com.duanml.reactorservice.middleware.reactor.consume;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用Reactor并发批处理抽象基类
 * ================================================
 * 1. 支持分布式场景下多线程批量消费Redis队列
 * 2. 支持worker动态调整
 * 3. 支持优雅停机
 * 4. 内置简单监控
 *
 * @param <T> 任务类型，如订单、消息等
 */
@Slf4j
public abstract class AbstractReactorConsumeBatch<T> {
    // 当前worker数量（线程数），可动态调整
    private final AtomicInteger workerCount = new AtomicInteger(8);

    // 是否运行中标志
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // Reactor调度器，适合I/O密集型任务调度
    private final Scheduler scheduler = Schedulers.boundedElastic();

    // 记录所有worker的句柄，用于停止和管理
    private final Set<Disposable> workerHandles = Collections.synchronizedSet(new java.util.HashSet<>());

    // 监控指标
    private final AtomicInteger completed = new AtomicInteger(0); // 完成数
    private final AtomicInteger failed = new AtomicInteger(0);    // 失败数
    private final AtomicInteger retried = new AtomicInteger(0);   // 重试数
    private final AtomicInteger discarded = new AtomicInteger(0); // 反序列化丢弃数

    // Redis队列
    protected final StringRedisTemplate redisTemplate;
    protected final String queueKey;

    // 任务最大重试次数
    protected volatile int maxRetry = 2;
    // 任务处理超时时间，0为不限
    protected volatile long taskTimeoutMillis = 0L;

    // 当前活跃worker数
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final Object shutdownLock = new Object(); // 优雅停机用锁

    /**
     * 构造方法
     * @param redisTemplate Redis操作模板
     * @param queueKey 队列key
     */
    public AbstractReactorConsumeBatch(StringRedisTemplate redisTemplate, String queueKey) {
        this.redisTemplate = redisTemplate;
        this.queueKey = queueKey;
    }

    /**
     * 启动批量消费
     * 会先调用stop()，保证不会重复启动
     * @param workerNum 启动worker线程数
     */
    public synchronized void start(int workerNum) {
        stop(); // 先停再启，防止重复启动
        workerCount.set(workerNum);
        isRunning.set(true);
        completed.set(0);
        failed.set(0);
        retried.set(0);
        discarded.set(0);
        for (int i = 0; i < workerCount.get(); i++) {
            Disposable handle = scheduler.schedule(workerRunnable(i + 1));
            workerHandles.add(handle);
        }
        log.info("分布式批处理启动, workerNum={}", workerNum);
    }

    /**
     * 优雅停机：通知所有worker自然退出，并等待全部退出
     */
    public void stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return; // 已经停止
        }
        log.info("分布式批处理优雅停机中，通知worker退出...");

        // 等待所有worker自然退出
        synchronized (shutdownLock) {
            while (activeWorkers.get() > 0) {
                try {
                    shutdownLock.wait(200);
                } catch (InterruptedException ignored) {
                }
            }
        }
        // 清理worker句柄
        workerHandles.forEach(Disposable::dispose);
        workerHandles.clear();
        onFinish();
        log.info("分布式批处理已优雅停止");
    }

    /**
     * 动态调整worker线程数
     * @param newCount 新线程总数
     */
    public synchronized void adjustWorkerCount(int newCount) {
        int oldCount = workerCount.get();
        if (newCount == oldCount) return;
        log.info("调整worker数量：{} -> {}", oldCount, newCount);
        if (newCount > oldCount) {
            // 增加新worker
            for (int i = oldCount; i < newCount; i++) {
                Disposable handle = scheduler.schedule(workerRunnable(i + 1));
                workerHandles.add(handle);
            }
            workerCount.set(newCount);
        } else if (newCount < oldCount) {
            // worker数量减少，多余worker自动检测到自己编号超限后自行退出
            workerCount.set(newCount);
        }
    }

    /**
     * worker线程处理主循环
     * @param workerId worker编号（1开始）
     */
    private Runnable workerRunnable(int workerId) {
        return () -> {
            activeWorkers.incrementAndGet();
            try {
                while (isRunning.get() && workerHandles.size() <= workerCount.get()) {
                    // 如果worker数量减少，超出worker自动退出
                    if (workerId > workerCount.get()) {
                        log.info("Worker-{} 超出当前worker数量限制, 退出", workerId);
                        break;
                    }
                    // 从Redis队列拉取任务
                    String taskStr = redisTemplate.opsForList().leftPop(queueKey, 2, TimeUnit.SECONDS);
                    if (taskStr == null) {
                        // 无任务，短暂休眠
                        try {
                            Thread.sleep(200);
                            log.warn("Worker-{} 无任务，休眠中...", workerId);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    // 反序列化任务
                    T task = null;
                    try {
                        task = deserializeTask(taskStr);
                    } catch (Exception ex) {
                        log.error("Worker-{} 任务反序列化失败: {}", workerId, ex.getMessage(), ex);
                        discarded.incrementAndGet();
                        onTaskDiscarded(taskStr, ex);
                        continue;
                    }

                    // 单个任务处理及重试
                    int retryCount = 0;
                    boolean success = false;
                    Exception lastEx = null;
                    do {
                        try {
                            if (taskTimeoutMillis > 0) {
                                // 带超时的处理
                                final T finalTask = task;
                                final Exception[] exHolder = new Exception[1];
                                Thread t = new Thread(() -> {
                                    try {
                                        handleTask(finalTask);
                                    } catch (Exception ex) {
                                        exHolder[0] = ex;
                                    }
                                });
                                t.start();
                                t.join(taskTimeoutMillis);
                                if (t.isAlive()) {
                                    t.interrupt();
                                    throw new RuntimeException("任务超时" + taskTimeoutMillis + "ms");
                                }
                                if (exHolder[0] != null) throw exHolder[0];
                            } else {
                                handleTask(task);
                            }
                            success = true;
                            completed.incrementAndGet();
                        } catch (Exception e) {
                            lastEx = e;
                            retryCount++;
                            retried.incrementAndGet();
                            onTaskError(task, e, retryCount);
                            log.error("Worker-{} -对应的任务为：{}, 处理任务异常，第{}次: {}",
                                    workerId, taskStr, retryCount, e.getMessage(), e);
                            if (retryCount <= maxRetry) {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                    } while (!success && retryCount <= maxRetry && isRunning.get());
                    if (!success) {
                        failed.incrementAndGet();
                        onTaskFailed(task, lastEx);
                        // TODO: 可扩展：失败任务入库、告警等
                    }
                }
            } finally {
                activeWorkers.decrementAndGet();
                synchronized (shutdownLock) {
                    shutdownLock.notifyAll();
                }
                log.info("Worker-{} 退出", workerId);
            }
        };
    }

    // ===================== 配置及监控相关 ========================

    /** 设置最大重试次数 */
    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    /** 设置单任务超时时间，单位ms */
    public void setTaskTimeoutMillis(long millis) {
        this.taskTimeoutMillis = millis;
    }

    public int getCompleted() {
        return completed.get();
    }

    public int getFailed() {
        return failed.get();
    }

    public int getRetried() {
        return retried.get();
    }

    public int getDiscarded() {
        return discarded.get();
    }

    public int getActiveWorkers() {
        return activeWorkers.get();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public int getWorkerCount() {
        return workerCount.get();
    }

    // =================== 子类需实现/可扩展 ===================

    /** 任务处理主逻辑，必须实现 */
    protected abstract void handleTask(T task) throws Exception;

    /** 任务反序列化，必须实现 */
    protected abstract T deserializeTask(String taskStr);

    /** 可选：批处理全部完成时钩子 */
    protected void onFinish() {
    }

    /** 可选：单任务处理出错时钩子 */
    protected void onTaskError(T task, Exception e, int retryCount) {
    }

    /** 可选：任务最终失败时钩子 */
    protected void onTaskFailed(T task, Exception e) {
    }

    /** 可选：反序列化失败丢弃钩子 */
    protected void onTaskDiscarded(String rawTask, Exception e) {
    }
}
