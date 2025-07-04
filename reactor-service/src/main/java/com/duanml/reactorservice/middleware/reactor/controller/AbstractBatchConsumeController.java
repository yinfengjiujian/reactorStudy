package com.duanml.reactorservice.middleware.reactor.controller;

import com.duanml.reactorservice.middleware.reactor.consume.AbstractReactorConsumeBatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 通用分布式批处理控制器基类
 * ====================================
 * 1. 适合所有节点“平级”的场景，每个节点既可响应本地控制，也可发全局指令。
 * 2. 自动监听全局指令(redis)，可动态调整worker数量。
 * 3. 状态自动上报，任意节点可聚合获取全局状态。
 * 4. 业务批处理节点只需继承本类即可，无需重复造轮子。
 *
 * @param <T> 业务数据类型（如订单、用户等）
 * @param <E> 具体批处理实现（需继承AbstractReactorConsumeBatch<T>）
 */
public abstract class AbstractBatchConsumeController<T, E extends AbstractReactorConsumeBatch<T>> {

    // 具体批处理执行器，由子类注入
    protected final E executor;

    // Redis操作模板，需注入
    protected final StringRedisTemplate redisTemplate;

    // 节点唯一标识，建议用主机名/IP/自定义ID，通过配置注入
    protected final String nodeId;

    // 全局指令key，所有节点监听此key
    private static final String GLOBAL_COMMAND_KEY = "batch:global:command";
    // 节点状态/workerNum等key前缀
    private static final String STATUS_KEY_PREFIX = "batch:node:";

    // 控制自感知线程的生命周期
    private final AtomicBoolean loopFlag = new AtomicBoolean(true);

    /**
     * 构造方法，注入具体批处理实现、redis模板、节点ID
     */
    public AbstractBatchConsumeController(E executor, StringRedisTemplate redisTemplate, String nodeId) {
        this.executor = executor;
        this.redisTemplate = redisTemplate;
        this.nodeId = nodeId;
        startCommandWatcher();   // 启动指令监听线程
        startStatusReporter();   // 启动状态上报线程
    }

    // ================= 全局操作API =================

    /**
     * 全局启动: 任意节点都可调用此接口，下发全局start指令，所有节点都会自动启动
     */
    @PostMapping("/startAll")
    public String startAll() {
        redisTemplate.opsForValue().set(GLOBAL_COMMAND_KEY, "start");
        return "全局批处理启动指令下发（由本节点" + nodeId + "发起）";
    }

    /**
     * 全局停止: 任意节点都可调用，所有节点自动停止
     */
    @PostMapping("/stopAll")
    public String stopAll() {
        redisTemplate.opsForValue().set(GLOBAL_COMMAND_KEY, "stop");
        return "全局批处理停止指令下发（由本节点" + nodeId + "发起）";
    }

    /**
     * 动态调整指定节点worker线程数
     *
     * @param nodeId    目标节点ID
     * @param workerNum 新线程数
     */
    @PostMapping("/setWorkerNum")
    public String setWorkerNum(@RequestParam String nodeId, @RequestParam int workerNum) {
        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + nodeId + ":workerNum", String.valueOf(workerNum));
        return "节点 " + nodeId + " 的 workerNum 已设置为 " + workerNum + "（由本节点" + this.nodeId + "发起）";
    }

    /**
     * 聚合查询所有节点的状态
     *
     * @return 节点状态列表
     */
    @GetMapping("/statusAll")
    public List<Map<String, Object>> statusAll() {
        Set<String> keys = redisTemplate.keys(STATUS_KEY_PREFIX + "*:status");
        if (keys == null || keys.isEmpty()) return Collections.emptyList();
        return keys.stream()
                .map(key -> {
                    Map<String, Object> nodeStatus = new LinkedHashMap<>();
                    String nodeId = key.substring(STATUS_KEY_PREFIX.length(), key.length() - ":status".length());
                    nodeStatus.put("nodeId", nodeId);
                    nodeStatus.put("status", redisTemplate.opsForValue().get(key));
                    return nodeStatus;
                }).collect(Collectors.toList());
    }

    // ================= 本节点操作API =================

    /**
     * 启动当前节点批处理
     *
     * @param workerNum 工作线程数（默认8）
     */
    @PostMapping("/start")
    public synchronized String start(@RequestParam(defaultValue = "8") int workerNum) {
        if (executor.getActiveWorkers() > 0 || executor.isRunning()) {
            return "批处理已在运行中，无需重复启动";
        }
        executor.start(workerNum);
        return "本节点批处理启动成功";
    }

    /**
     * 停止本节点批处理
     */
    @PostMapping("/stop")
    public String stop() {
        executor.stop();
        return "本节点批处理优雅停机";
    }

    /**
     * 查询本节点批处理状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "isRunning", executor.isRunning(),
                "workerNum", executor.getWorkerCount(),
                "activeWorkers", executor.getActiveWorkers(),
                "completed", executor.getCompleted(),
                "failed", executor.getFailed(),
                "retried", executor.getRetried(),
                "discarded", executor.getDiscarded()
        );
    }

    // ================= 指令监听线程 =================

    /**
     * 启动后台线程，自动监听全局指令和workerNum变更，自动调整本地批处理
     */
    private void startCommandWatcher() {
        Thread watcher = new Thread(() -> {
            String lastCmd = "";
            int lastWorkerNum = -1;
            while (loopFlag.get()) {
                try {
                    // 读取全局指令
                    String cmd = redisTemplate.opsForValue().get(GLOBAL_COMMAND_KEY);
                    // 读取本节点workerNum配置，若无则用8
                    String workerNumStr = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + nodeId + ":workerNum");
                    int workerNum = (workerNumStr != null) ? Integer.parseInt(workerNumStr) : 8;

                    // 只要指令或workerNum有变化就自动调整
                    if (!Objects.equals(cmd, lastCmd) || workerNum != lastWorkerNum) {
                        lastCmd = cmd;
                        lastWorkerNum = workerNum;

                        // 优雅停机后再启动
                        if ("start".equals(cmd) && (!executor.isRunning() || workerNum != executor.getWorkerCount())) {
                            executor.stop();
                            executor.start(workerNum);
                        }
                        if ("stop".equals(cmd) && executor.isRunning()) {
                            executor.stop();
                        }
                    }
                    Thread.sleep(3000); // 每3秒检查一次
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Command watcher error: " + e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }, "batch-command-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    // ================= 状态上报线程 =================

    /**
     * 启动后台线程，定时上报本节点状态到Redis，供全局聚合用
     */
    private void startStatusReporter() {
        Thread reporter = new Thread(() -> {
            while (loopFlag.get()) {
                try {
                    Map<String, Object> stat = Map.of(
                            "isRunning", executor.isRunning(),
                            "workerNum", executor.getWorkerCount(),
                            "activeWorkers", executor.getActiveWorkers(),
                            "completed", executor.getCompleted(),
                            "failed", executor.getFailed(),
                            "retried", executor.getRetried(),
                            "discarded", executor.getDiscarded()
                    );
                    redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + nodeId + ":status", stat.toString());
                    Thread.sleep(2000); // 每2秒上报一次
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Status report error: " + e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }, "batch-status-reporter");
        reporter.setDaemon(true);
        reporter.start();
    }

}
