package com.duanml.reactorstudy.reactor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>Title: com.duanml.reactorstudy.reactor</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/2 23:27
 * Description: No Description
 * ReactorBatchExecutor<T>
 *     T  是一个泛型，支持复查业务对象
 *
 */
@Slf4j
@Component
public class MyBusinessJob extends ReactorBatchExecutor<Integer> {

    @Override
    protected void handleTask(Integer task) throws Exception {
        // 你的单个任务处理逻辑
        log.info("处理任务: {}", task);
        // ... 业务代码 ...
    }

    @Override
    protected void onFinish() {
        log.info("全部任务处理完毕!");
    }

    @Override
    protected void onTaskError(Integer task, Exception e, int retryCount) {
        log.error("任务处理失败: {}, 第{}次: {}", task, retryCount, e.getMessage());
    }
}
