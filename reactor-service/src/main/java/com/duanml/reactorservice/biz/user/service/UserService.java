package com.duanml.reactorservice.biz.user.service;



import com.baomidou.mybatisplus.extension.service.IService;
import com.duanml.reactorservice.biz.user.entity.User;

import java.util.List;

/**
 * <p>Title: com.duanml.reactorstudy.biz.user.service</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/1 23:05
 * Description: No Description
 */
public interface UserService extends IService<User> {

    List<User> getAllUsers();

    void insertUserBatch(List<User> users);
}
