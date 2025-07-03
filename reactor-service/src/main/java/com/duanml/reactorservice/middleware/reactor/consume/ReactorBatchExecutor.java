package com.duanml.reactorservice.middleware.reactor.consume;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
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
public abstract class ReactorBatchExecutor<T> {
    // 可动态调整worker数
    private final AtomicInteger workerCount = new AtomicInteger(4);
    // 任务队列
    private final Queue<T> taskQueue = new ConcurrentLinkedQueue<>();
    // 正在处理的任务
    private final Set<T> runningTasks = new HashSet<>();
    // 是否运行中
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    // 调度器
    private final Scheduler scheduler = Schedulers.boundedElastic();
    // 最大重试次数
    private int maxRetry = 0;
    // 任务超时ms，<=0为不限制
    private long taskTimeoutMillis = 0L;
    // 统计
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger retried = new AtomicInteger(0);

    // worker句柄，便于优雅关闭
    private final Set<Disposable> workerHandles = new HashSet<>();

    /**
     * 启动任务处理
     */
    public void start(Iterable<T> tasks, int workerNum) {
        stop(); // 防止多次启动
        log.info("启动批处理任务, workerNum={}", workerNum);
        workerCount.set(workerNum);
        isRunning.set(true);
        taskQueue.clear();
        runningTasks.clear();
        completed.set(0);
        failed.set(0);
        retried.set(0);
        tasks.forEach(taskQueue::offer);

        for (int i = 0; i < workerCount.get(); i++) {
            Disposable handle = scheduler.schedule(workerRunnable(i + 1));
            workerHandles.add(handle);
        }
    }

    /**
     * 优雅停止
     */
    public void stop() {
        isRunning.set(false);
        workerHandles.forEach(Disposable::dispose);
        workerHandles.clear();
        log.info("批处理任务已停止");
    }

    /**
     * 设置最大重试次数（默认3）
     */
    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    /**
     * 设置单个任务超时时间（ms）（默认无限制）
     */
    public void setTaskTimeoutMillis(long millis) {
        this.taskTimeoutMillis = millis;
    }

    /**
     * 动态调整并发worker数（增加实时生效，减少下次启动生效）
     */
    public void setWorkerCount(int count) {
        this.workerCount.set(count);
    }

    // 你只需实现
    protected abstract void handleTask(T task) throws Exception;

    // 可选：任务处理完成时回调
    protected void onFinish() {
    }

    // 可选：单个任务失败时回调
    protected void onTaskError(T task, Exception e, int retryCount) {
    }

    /**
     * 获取队列剩余
     */
    public int getQueueSize() {
        return taskQueue.size();
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

    private Runnable workerRunnable(int workerId) {
        return () -> {
            while (isRunning.get()) {
                T task = taskQueue.poll();
                if (task == null) {
                    if (runningTasks.isEmpty()) {
                        log.info("Worker-{} 队列空，所有任务完成", workerId);
                        isRunning.set(false);
                        onFinish();
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                runningTasks.add(task);
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
                        log.warn("Worker-{} 处理任务异常，第{}次: {}", workerId, retryCount, e.getMessage(), e);
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
                }
                runningTasks.remove(task);
            }
            log.info("Worker-{} 退出", workerId);
        };
    }
}
