package io.github.wenjunxiao.zookeeper.job;

import com.dangdang.ddframe.job.api.ElasticJob;
import com.dangdang.ddframe.job.config.JobCoreConfiguration;
import com.dangdang.ddframe.job.config.dataflow.DataflowJobConfiguration;
import com.dangdang.ddframe.job.lite.api.JobScheduler;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.lite.spring.api.SpringJobScheduler;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ElasticJobService {
    @Autowired
    private RegCenterConfig config;
    private final Map<String, RegistryCenterProxy> proxyMap = new LinkedHashMap<>();
    private String leaderServer = null;

    public String getNamespace() {
        return config.getNamespace();
    }

    @PostConstruct
    public void initAndStartAllJob() {
        for (int i = 0; i < config.getServerList().size(); i++) {
            String server = config.getServerList().get(i);
            RegistryCenterProxy regCenter = new RegistryCenterProxy(server, config.getNamespace());
            regCenter.init();
            proxyMap.put(server, regCenter);
            String jobName = ElasticTestJob.class.getName() + (i + 1);
            ElasticJob elasticJob = new ElasticTestJob(server, regCenter);
            JobCoreConfiguration coreConfiguration = JobCoreConfiguration.newBuilder(jobName, "0 0 12 * * ?", 1)
                    .jobParameter("10000*2").description(server).failover(true)
                    .build();
            DataflowJobConfiguration dataflowJobConfiguration = new DataflowJobConfiguration(coreConfiguration, ElasticTestJob.class.getName(), false);
            JobScheduler jobScheduler = new SpringJobScheduler(elasticJob, regCenter,
                    LiteJobConfiguration.newBuilder(dataflowJobConfiguration)
                            .monitorExecution(false)
                            .disabled(false)
                            .overwrite(true)
                            .build());
            jobScheduler.init();
            if (zkCmd(server, "srvr").stream().anyMatch(line -> line.contains("leader"))) {
                leaderServer = server;
            }
        }
    }

    public RegistryCenterProxy get(String key) {
        return proxyMap.get(key);
    }

    public RegistryCenterProxy getMaster() {
        return proxyMap.get(leaderServer);
    }

    public Map<String, Object> getAllSession() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leader", leaderServer);
        for (String server : proxyMap.keySet()) {
            Map<String, Object> res = new LinkedHashMap<>();
            ZooKeeper zooKeeper = proxyMap.get(server).getZooKeeper();
            res.put("zookeeper", zooKeeper.toString());
            res.put("sessionId", "0x" + Long.toHexString(zooKeeper.getSessionId()));
            res.put("sessionPassword", Base64.encodeBase64String(zooKeeper.getSessionPasswd()));
            result.put(server, res);
        }
        return result;
    }

    private List<String> zkCmd(String server, String cmd) {
        Socket socket = new Socket();
        String[] arr = server.split(":");
        List<String> lines = new ArrayList<>();
        try {
            socket.connect(new InetSocketAddress(arr[0], Integer.parseInt(arr[1])));
            socket.getOutputStream().write(cmd.getBytes(StandardCharsets.UTF_8));
            try (Scanner scanner = new Scanner(socket.getInputStream())) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return lines;
    }

    public Map<String, Object> getAllConnections() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leader", leaderServer);
        for (String server : proxyMap.keySet()) {
            Socket socket = new Socket();
            String[] arr = server.split(":");
            List<String> lines = zkCmd(server, "cons")
                    .stream().filter(line -> line.contains("sid="))
                    .collect(Collectors.toList());
            result.put(server, lines);
        }
        return result;
    }

}
