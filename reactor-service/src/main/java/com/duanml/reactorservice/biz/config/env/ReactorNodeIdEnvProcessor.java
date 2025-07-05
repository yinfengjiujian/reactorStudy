package com.duanml.reactorservice.biz.config.env;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.InetAddress;

/**
 * <p>Title: com.duanml.reactorservice.biz.config.env</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/5 08:18
 * Description: No Description
 */
public class ReactorNodeIdEnvProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            // 优先读取 server.port，没有就用默认端口（如8080）
            String port = environment.getProperty("server.port", "8080");
            String nodeId = ip + ":" + port;
            environment.getSystemProperties().put("reactor-node.id", nodeId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate reactor-node.id", e);
        }
    }

}
