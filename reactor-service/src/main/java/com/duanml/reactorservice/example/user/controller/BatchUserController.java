package com.duanml.reactorservice.example.user.controller;

import com.duanml.reactorservice.biz.user.entity.User;
import com.duanml.reactorservice.example.user.service.BatchUserService;
import com.duanml.reactorservice.middleware.reactor.controller.AbstractBatchProducerController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>Title: com.duanml.reactorstudy.biz.user.controller</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/3 20:42
 * Description: No Description
 */
@RestController
@RequestMapping("/api/batch/produce/user")
public class BatchUserController extends AbstractBatchProducerController<User> {

    public BatchUserController(BatchUserService batchProducer) {
        super(batchProducer);
    }
}
