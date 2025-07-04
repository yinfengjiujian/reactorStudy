package com.duanml.reactorservice.biz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>Title: com.duanml.reactorservice.biz.config</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/4 21:56
 * Description: No Description
 */
@Component
@ConfigurationProperties(prefix = "reactor-node")
public class NodeProperties {
    private String id;

    // getter & setter
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }
}
