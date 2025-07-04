package com.duanml.reactorservice.example.user.controller;

import com.duanml.reactorservice.example.user.service.UserTaskConsumeServiceBatch;
import com.duanml.reactorservice.middleware.reactor.controller.AbstractBatchConsumeController;
import com.duanml.user.UserTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>Title: com.duanml.reactorservice.example.user.controller</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/4 11:30
 * Description: No Description
 */
@RestController
@RequestMapping("/api/user/task/consume")
public class UserTaskConsumeController extends AbstractBatchConsumeController<UserTask, UserTaskConsumeServiceBatch> {

    @Autowired
    public UserTaskConsumeController(UserTaskConsumeServiceBatch executor) {
        super(executor);
    }

}
