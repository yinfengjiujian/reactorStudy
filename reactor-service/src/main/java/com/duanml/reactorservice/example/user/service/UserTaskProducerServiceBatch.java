package com.duanml.reactorservice.example.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duanml.reactorservice.biz.user.entity.User;
import com.duanml.reactorservice.biz.user.service.UserService;
import com.duanml.reactorservice.middleware.reactor.produce.AbstractReactorProducerBatch;
import com.duanml.reactorservice.utils.JacksonUtil;
import com.duanml.user.UserTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
public class UserTaskProducerServiceBatch extends AbstractReactorProducerBatch<UserTask> {

    @Resource
    private UserService userService;

    private final static String QUEUE_KEY = "userTask:batch:queue";

    private final static Integer QUEUE_SIZE = 50_000;

    private final static Integer PAGE_SIZE = 5000;

    private final static String BLOOM_KEY = "userTask:batch:dedup:bloom";

    public UserTaskProducerServiceBatch(StringRedisTemplate redisTemplate) {
        super(redisTemplate, QUEUE_KEY, QUEUE_SIZE, PAGE_SIZE, DedupType.BLOOM, BLOOM_KEY);
    }

    @Override
    protected List<UserTask> fetchBatch(int offset, int limit) {
        log.info("UserTask fetchBatch offset: {}, limit: {}", offset, limit);

        // 构造分页对象：MyBatis-Plus 的 Page 默认页码从 1 开始
        int page = offset / limit + 1;
        Page<User> userPage = new Page<>(page, limit);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNull("phone");
        queryWrapper.orderByAsc("id");

        Page<User> userListPage = userService.page(userPage, queryWrapper);
        List<UserTask> collect = userListPage.getRecords().stream().map(user -> {
            UserTask userTask = new UserTask();
            userTask.setId(user.getId());
            userTask.setUsername(user.getUsername());
            userTask.setPassword(user.getPassword());
            userTask.setEmail(user.getEmail());
            userTask.setCreatedAt(user.getCreatedAt());
            return userTask;
        }).collect(Collectors.toList());
        return collect;
    }

    @Override
    protected String toJson(UserTask task) {
        return JacksonUtil.toJson(task);
    }

    /**
     * 这里返回的key  是用来redis做防止消息重复入队列用的
     * @param task
     * @return
     */
    @Override
    protected String getPrimaryKey(UserTask task) {
        return String.valueOf(task.getId());
    }


}
