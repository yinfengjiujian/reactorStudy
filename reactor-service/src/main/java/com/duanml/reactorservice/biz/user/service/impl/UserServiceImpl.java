package com.duanml.reactorservice.biz.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.duanml.reactorservice.biz.user.entity.User;
import com.duanml.reactorservice.biz.user.mapper.UserMapper;
import com.duanml.reactorservice.biz.user.service.UserService;
import com.duanml.reactorservice.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>Title: com.duanml.reactorstudy.biz.user.service.impl</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/1 23:06
 * Description: No Description
 */
@Slf4j
@Service
@Transactional(rollbackFor = Throwable.class)
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public List<User> getAllUsers() {
        List<User> users1 = this.baseMapper.selectByUsername("bob");
        log.info("直接XML的SQL查询返回=》》》》》》》》》》》: {}", JacksonUtil.toJson(users1));
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", "bob");
        List<User> users = this.baseMapper.selectList(wrapper);
        log.error("QueryWrapper查询返回=》》》》》》》》》》》: {}", JacksonUtil.toJson(users));
        return users;
    }

    @Override
    public void insertUserBatch(List<User> users) {
        this.saveBatch(users);
    }
}
