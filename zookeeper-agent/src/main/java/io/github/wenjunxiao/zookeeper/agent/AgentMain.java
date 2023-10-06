package io.github.wenjunxiao.zookeeper.agent;

import java.lang.instrument.Instrumentation;

public class AgentMain {

    public static void premain(String args, Instrumentation inst) throws Exception {
        inst.addTransformer(new ZookeeperTransformer());
    }
}
