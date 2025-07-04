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
 * <p>Title: com.duanml.reactorstudy.reactor</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/2 23:11
 *
 * @param <T> 任务类型
 *            Description: 通用Reactor并发批处理
 */
@Slf4j
public abstract class AbstractReactorBatchExecutor<T> {
    // 默认worker数量
    private final AtomicInteger workerCount = new AtomicInteger(8);
    // 是否运行中
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    // 使用Reactor调度器 这个适合I/O密集型任务
    private final Scheduler scheduler = Schedulers.boundedElastic();
    // 多线程安全的worker句柄集合
    private final Set<Disposable> workerHandles = Collections.synchronizedSet(new java.util.HashSet<>());
    // 监控指标
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger retried = new AtomicInteger(0);
    private final AtomicInteger discarded = new AtomicInteger(0);

    // Redis队列配置
    protected final StringRedisTemplate redisTemplate;
    protected final String queueKey;
    protected volatile int maxRetry = 2;
    protected volatile long taskTimeoutMillis = 0L;

    // 优雅停机辅助
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final Object shutdownLock = new Object();

    public AbstractReactorBatchExecutor(StringRedisTemplate redisTemplate, String queueKey) {
        this.redisTemplate = redisTemplate;
        this.queueKey = queueKey;
    }

    /**
     * 启动消费
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
     * 优雅停机：等待worker自然退出
     */
    public void stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return; // 已经停止
        }
        // 提示worker退出
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
     * 动态调整worker数量
     */
    public synchronized void adjustWorkerCount(int newCount) {
        int oldCount = workerCount.get();
        if (newCount == oldCount) return;
        log.info("调整worker数量：{} -> {}", oldCount, newCount);
        if (newCount > oldCount) {
            for (int i = oldCount; i < newCount; i++) {
                Disposable handle = scheduler.schedule(workerRunnable(i + 1));
                workerHandles.add(handle);
            }
            workerCount.set(newCount);
        } else if (newCount < oldCount) {
            workerCount.set(newCount);
            // 多余worker自动自然退出（见workerRunnable中的判断）
        }
    }


    // ================ worker核心逻辑 ================
    private Runnable workerRunnable(int workerId) {
        return () -> {
            activeWorkers.incrementAndGet();
            try {
                while (isRunning.get() && workerHandles.size() <= workerCount.get()) {
                    // 如果worker数量减少，部分worker自动退出
                    if (workerId > workerCount.get()) {
                        log.info("Worker-{} 超出当前worker数量限制, 退出", workerId);
                        break;
                    }
                    // 从Redis队列中获取任务
                    String taskStr = redisTemplate.opsForList().leftPop(queueKey, 2, TimeUnit.SECONDS);
                    if (taskStr == null) {
                        try {
                            Thread.sleep(200);
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

                    // 单个处理任务逻辑
                    int retryCount = 0;
                    boolean success = false;
                    Exception lastEx = null;
                    do {
                        try {
                            if (taskTimeoutMillis > 0) {
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
                        // 可扩展记录到失败队列或报警等
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

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public void setTaskTimeoutMillis(long millis) {
        this.taskTimeoutMillis = millis;
    }

    // ===================== 监控相关 ========================

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

    public int getWorkerCount() {
        return workerCount.get();
    }

    // =================== 子类实现区域 ===================
    // 任务处理
    protected abstract void handleTask(T task) throws Exception;
    // 任务反序列化
    protected abstract T deserializeTask(String taskStr);

    // 可选：批处理全部完成时
    protected void onFinish() {
    }

    // 可选：单个任务错误时
    protected void onTaskError(T task, Exception e, int retryCount) {
    }

    // 可选：任务最终失败时
    protected void onTaskFailed(T task, Exception e) {
    }

    // 可选：反序列化丢弃
    protected void onTaskDiscarded(String rawTask, Exception e) {
    }
}
