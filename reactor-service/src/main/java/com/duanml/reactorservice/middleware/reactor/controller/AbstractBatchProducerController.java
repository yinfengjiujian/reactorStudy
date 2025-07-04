package com.duanml.reactorservice.middleware.reactor.controller;

import com.duanml.reactorservice.middleware.reactor.produce.AbstractReactorProducerBatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * <p>Title: com.duanml.reactorstudy.middleware.reactor.controller</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/3 20:34
 * Description: 批量生产任务控制器基类
 */
public abstract class AbstractBatchProducerController<T> {

    protected final AbstractReactorProducerBatch<T> batchProducer;

    public AbstractBatchProducerController(AbstractReactorProducerBatch<T> batchProducer) {
        this.batchProducer = batchProducer;
    }

    @PostMapping("/start")
    public String start() {
        batchProducer.startProduce();
        return "Producer started";
    }

    @PostMapping("/stop")
    public String stop() {
        batchProducer.requestStop();
        return "Producer stop requested";
    }

    @GetMapping("/status")
    public AbstractReactorProducerBatch.ProducerStatus status() {
        return batchProducer.status();
    }
}
