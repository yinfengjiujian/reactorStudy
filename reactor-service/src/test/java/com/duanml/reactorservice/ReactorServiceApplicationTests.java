package com.duanml.reactorservice;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.duanml.reactorservice.biz.user.entity.User;
import com.duanml.reactorservice.biz.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Random;

@SpringBootTest
@Slf4j
class ReactorServiceApplicationTests {

    @Autowired
    private UserService userService;

    @Test
    void contextLoads() {
    }



    @Test
    public void batchInsertUsers() {
        int total = 5_000_000;
        int batchSize = 1000; // 每批插入1000条
        List<User> batch = new ArrayList<>(batchSize);

        long start = System.currentTimeMillis();
        Random random = new Random();

        Integer insert = 0;
        for (int i = 1; i <= total; i++) {
            User user = new User();


            // 随机用户名：user_加10位小写字母或数字
            String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

            // 随机密码：12位字母和数字
            String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder pwdBuilder = new StringBuilder();
            for (int j = 0; j < 12; j++) {
                pwdBuilder.append(chars.charAt(random.nextInt(chars.length())));
            }
            String password = pwdBuilder.toString();

            // 随机邮箱：user_加6位小写字母或数字 @test.com
            String email = "user_" +
                    UUID.randomUUID().toString().replace("-", "").substring(0, 6) +
                    "@test.com";


            // id自增，不赋值
            user.setId(IdWorker.getId());
            user.setUsername(username);
            user.setPassword(password);
            user.setEmail(email);
            user.setPhone(null); // phone字段留空
            user.setCreatedAt(new Date());

            batch.add(user);

            if (batch.size() == batchSize || i == total) {
                userService.insertUserBatch(batch); // 使用MyBatis-Plus批量插入
                batch.clear();
                insert++; // 统计插入次数
                // 可选：每插入一批打印进度
                log.warn("already Inserted batch User: {} doing=====>>>>>>>>", insert);
            }
        }

        long end = System.currentTimeMillis();
        log.error("插入500万用户耗时: " + (end - start) / 1000.0 + " 秒");
    }

}
