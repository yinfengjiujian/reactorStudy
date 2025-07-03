package com.duanml.reactorservice.biz.user.controller;


import com.duanml.reactorservice.biz.user.entity.User;
import com.duanml.reactorservice.biz.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * <p>Title: com.duanml.reactorstudy.biz.user.controller</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/1 19:35
 * Description: No Description
 */
@RestController
@RequestMapping(value = "/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/getUser")
    public User getUser() {
        List<User> allUsers = userService.getAllUsers();
        User user = new User();
        user.setId(1);
        user.setUsername("独孤九剑");
        user.setPassword("web3@demo.io");
        user.setEmail("duanml@qq.com");
        user.setUserId(3994934943582349L);
        user.setCreatedAt(new Date());
        return user;
    }


}
