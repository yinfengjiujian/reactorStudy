package com.duanml.reactorservice.middleware.reactor.controller;

import com.duanml.reactorservice.middleware.reactor.produce.AbstractReactorProducerBatch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>Title: com.duanml.reactorstudy.middleware.reactor.controller</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/3 20:34
 * Description: 批量生产任务控制器基类
 *
 * 批量生产任务控制器基类，分布式锁+Redis停止指令实现多节点唯一生产和任意节点可停
 *
 */
public abstract class AbstractBatchProducerController<T> {

    protected final AbstractReactorProducerBatch<T> batchProducer;
    protected final RedissonClient redissonClient;
    protected final StringRedisTemplate redisTemplate;

    private static final String PRODUCER_LOCK_KEY = "reactor:producer:lock";
    private static final String PRODUCER_STOP_KEY = "reactor:producer:stop";

    // 标记当前节点是否持有锁
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    public AbstractBatchProducerController(AbstractReactorProducerBatch<T> batchProducer,
                                           RedissonClient redissonClient,
                                           StringRedisTemplate redisTemplate) {
        this.batchProducer = batchProducer;
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;

        // 持锁节点定时检查停止信号
        startStopSignalWatcher();
    }

    @RequestMapping("/start")
    public synchronized String start() {
        RLock lock = redissonClient.getLock(PRODUCER_LOCK_KEY);
        boolean locked = false;
        try {
            // 自动续期的分布式锁（Redisson看门狗机制），不设置leaseTime
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (locked) {
                isLeader.set(true);
                redisTemplate.delete(PRODUCER_STOP_KEY); // 启动前清理旧的stop信号
                batchProducer.startProduce();
                return "Producer started (distributed lock acquired)";
            } else {
                return "Producer already started on another node (distributed lock not acquired)";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Producer start interrupted";
        }
    }

    @RequestMapping("/stop")
    public String stop() {
        // 不管在哪个节点，写入停止信号
        redisTemplate.opsForValue().set(PRODUCER_STOP_KEY, "1");
        return "Producer stop signal sent";
    }

    @RequestMapping("/status")
    public AbstractReactorProducerBatch.ProducerStatus status() {
        return batchProducer.status();
    }

    /**
     * 持锁节点定时检查停止信号，收到则优雅停机并释放锁
     */
    private void startStopSignalWatcher() {
        // 可用更优方式如调度线程池/定时任务，这里用简单线程演示
        Thread watcher = new Thread(() -> {
            while (true) {
                try {
                    // 5秒检查一次
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
                if (isLeader.get() && "1".equals(redisTemplate.opsForValue().get(PRODUCER_STOP_KEY))) {
                    batchProducer.requestStop();
                    // 释放分布式锁
                    RLock lock = redissonClient.getLock(PRODUCER_LOCK_KEY);
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                    isLeader.set(false);
                    // 清理停止信号
                    redisTemplate.delete(PRODUCER_STOP_KEY);
                }
            }
        }, "producer-stop-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

}
