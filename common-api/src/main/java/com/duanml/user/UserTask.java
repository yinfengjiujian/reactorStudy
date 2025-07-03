package com.duanml.user;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * <p>Title: com.duanml.user</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/3 21:58
 * Description: No Description
 */
@Data
@Builder
public class UserTask {

    private Integer id;

    private String username;

    private String password;

    private String email;

    private Long userId;

    private Date createdAt;
}
