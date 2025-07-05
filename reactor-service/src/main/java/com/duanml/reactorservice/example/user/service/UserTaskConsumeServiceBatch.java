package com.duanml.reactorservice.example.user.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.duanml.reactorservice.biz.user.entity.User;
import com.duanml.reactorservice.biz.user.service.UserService;
import com.duanml.reactorservice.middleware.reactor.consume.AbstractReactorConsumeBatch;
import com.duanml.reactorservice.utils.JacksonUtil;
import com.duanml.user.UserTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Random;

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

    @Resource
    private UserService userService;

    private final static String QUEUE_KEY = "userTask:batch:queue";

    public UserTaskConsumeServiceBatch(StringRedisTemplate redisTemplate) {
        super(redisTemplate, QUEUE_KEY);
    }

    /**
     * 需要幂等消费，必须这样做，避免重复消费
     * @param task
     * @throws Exception
     */
    @Override
    protected void handleTask(UserTask task) throws Exception {
        User serviceById = userService.getById(task.getId());
        if (StringUtils.isNotBlank(serviceById.getPhone())) {
            // 如果手机号已存在，跳过处理,已经处理过了不再处理，达到幂等消费的目的
            log.warn("手机号已存在，跳过处理: {}", serviceById.getId());
            return;
        }
        // 模拟实际耗时，这里睡眠500毫秒
        Thread.sleep(50);
        // 常见运营商号段（简化，仅列举部分）
        String[] prefixes = {"133", "149", "153", "173", "177", "180", "181", "189", // 电信
                "130", "131", "132", "145", "155", "156", "166", "175", "176", "185", "186", // 联通
                "134", "135", "136", "137", "138", "139", "147", "150", "151", "152", "157", "158", "159", "178", "182", "183", "184", "187", "188", "198"}; // 移动

        Random random = new Random();
        String prefix = prefixes[random.nextInt(prefixes.length)];

        // 生成后8位随机数字
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            suffix.append(random.nextInt(10));
        }
        String phone = prefix + suffix;

        User user = new User();
        user.setPhone(phone);
        user.setCreatedAt(new Date());

        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", task.getId());
        userService.update(user, updateWrapper);
    }

    @Override
    protected void onFinish() {
        log.warn("\n" +
                "================= 🚨 任务状态提示 🚨 =================\n" +
                ">>>>> 全部任务处理完毕！\n" +
                ">>>>> 请确认是否有遗漏或异常未处理。\n" +
                "======================================================");
    }

    @Override
    protected void onTaskError(UserTask task, Exception e, int retryCount) {
        log.error("任务处理失败: {}, 第{}次: {}", JacksonUtil.toJson(task), retryCount, e.getMessage());
    }

    @Override
    protected UserTask deserializeTask(String taskStr) {
        return JacksonUtil.fromJson(taskStr, UserTask.class);
    }
}
