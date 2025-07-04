package com.duanml.reactorservice.middleware.reactor.controller;

import com.duanml.reactorservice.middleware.reactor.consume.AbstractReactorConsumeBatch;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * <p>Title: com.duanml.reactorservice.middleware.reactor.controller</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/4 11:11
 * Description: No Description
 */
public abstract class AbstractBatchConsumeController<T, E extends AbstractReactorConsumeBatch<T>> {

    protected final E executor;

    public AbstractBatchConsumeController(E executor) {
        this.executor = executor;
    }

    @RequestMapping("/start")
    public synchronized String start(@RequestParam(defaultValue = "8") int workerNum) {
        if (executor.getActiveWorkers() > 0 || executor.isRunning()) {
            return "批处理已在运行中，无需重复启动";
        }
        executor.start(workerNum);
        return "批处理启动成功";
    }

    @RequestMapping("/stop")
    public String stop() {
        executor.stop();
        return "批处理优雅停机";
    }

    @RequestMapping("/status")
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

}
