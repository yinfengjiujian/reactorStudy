package com.duanml.reactorservice.example.user.service;

import com.duanml.reactorservice.middleware.reactor.produce.AbstractBatchProducer;
import com.duanml.user.UserTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>Title: com.duanml.reactorstudy.example.user.service</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/3 20:50
 * Description: 用户批量生产者服务 入队列
 */
@Slf4j
@Service
public class BatchUserService extends AbstractBatchProducer<UserTask> {

    private final static String QUEUE_KEY = "userTask:batch:queue";

    private final static Integer QUEUE_SIZE = 200_000;

    private final static Integer PAGE_SIZE = 5000;

    public BatchUserService(StringRedisTemplate redisTemplate) {
        super(redisTemplate, QUEUE_KEY, QUEUE_SIZE, PAGE_SIZE);
    }

    @Override
    protected List<UserTask> fetchBatch(int offset, int limit) {
        log.info("UserTask fetchBatch offset: {}, limit: {}", offset, limit);
        return List.of();
    }

    @Override
    protected String toJson(UserTask task) {
        return "";
    }
}
