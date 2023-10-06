package io.github.wenjunxiao.zookeeper.job;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "regCenter")
public class RegCenterConfig {
    private String namespace;

    private List<String> serverList;

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setServerList(List<String> serverList) {
        this.serverList = serverList;
    }

    public List<String> getServerList() {
        return serverList;
    }
}
