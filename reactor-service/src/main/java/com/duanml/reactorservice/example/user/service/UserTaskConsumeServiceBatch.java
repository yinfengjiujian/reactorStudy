package com.duanml.reactorservice.example.user.service;

import com.duanml.reactorservice.middleware.reactor.consume.AbstractReactorConsumeBatch;
import com.duanml.reactorservice.utils.JacksonUtil;
import com.duanml.user.UserTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>Title: com.duanml.reactorservice.example.user.service</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/4 10:56
 * Description: No Description
 */
@Slf4j
@Service
public class UserTaskConsumeServiceBatch extends AbstractReactorConsumeBatch<UserTask> {

    private final static String QUEUE_KEY = "userTask:batch:queue";

    public UserTaskConsumeServiceBatch(StringRedisTemplate redisTemplate) {
        super(redisTemplate, QUEUE_KEY);
    }

    @Override
    protected void handleTask(UserTask task) throws Exception {
        // 你的单个任务处理逻辑
        log.info("处理任务: {}", task);
        // ... 业务代码 ...
    }

    @Override
    protected void onFinish() {
        log.info("全部任务处理完毕!");
    }

    @Override
    protected void onTaskError(UserTask task, Exception e, int retryCount) {
        log.error("任务处理失败: {}, 第{}次: {}", task, retryCount, e.getMessage());
    }

    @Override
    protected UserTask deserializeTask(String taskStr) {
        return JacksonUtil.fromJson(taskStr, UserTask.class);
    }
}
