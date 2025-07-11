package com.duanml.reactorservice.example.user.controller;

import com.duanml.reactorservice.example.user.service.UserTaskProducerServiceBatch;
import com.duanml.reactorservice.middleware.reactor.controller.AbstractBatchProducerController;
import com.duanml.user.UserTask;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
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
@RequestMapping("/api/user/task/produce")
public class UserTaskProducerController extends AbstractBatchProducerController<UserTask> {

    public UserTaskProducerController(UserTaskProducerServiceBatch batchProducer,
                                      RedissonClient redissonClient,
                                      StringRedisTemplate redisTemplate) {

        super(batchProducer, redissonClient, redisTemplate);
    }
}
