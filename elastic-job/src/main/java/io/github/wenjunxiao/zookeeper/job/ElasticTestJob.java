package io.github.wenjunxiao.zookeeper.job;

import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ElasticTestJob implements SimpleJob {
    private final Logger log = LoggerFactory.getLogger(ElasticTestJob.class);

    private final String server;
    private final RegistryCenterProxy regCenter;

    public ElasticTestJob(String server, RegistryCenterProxy regCenter) {
        log.info("[" + server + "] init {}", regCenter);
        this.server = server;
        this.regCenter = regCenter;
    }

    @Override
    public void execute(ShardingContext shardingContext) {
        log.info("[" + server + "] start {}", regCenter);
        String[] params = shardingContext.getJobParameter().split("\\*");
        long time = params.length > 0 ? Long.parseLong(params[0]) : 10000;
        int count = params.length > 1 ? Integer.parseInt(params[1]) : 1;
        while (count-- > 0) {
            log.info("[" + server + "] running {} {}", count, regCenter);
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (regCenter.isSessionMoved()) {
                log.info("[" + server + "] recover from session moved");
                regCenter.recoverFromSessionMoved();
                count++;
            }
        }
        log.info("[" + server + "] end {}", regCenter);
    }
}
