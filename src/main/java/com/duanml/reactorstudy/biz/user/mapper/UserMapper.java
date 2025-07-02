package com.duanml.reactorstudy.biz.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanml.reactorstudy.biz.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>Title: com.duanml.reactorstudy.biz.user.dao</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/1 23:02
 * Description: No Description
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    List<User> selectByUsername(@Param(value = "username") String username);

}
