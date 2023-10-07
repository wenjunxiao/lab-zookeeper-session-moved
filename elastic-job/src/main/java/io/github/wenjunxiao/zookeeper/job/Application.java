package io.github.wenjunxiao.zookeeper.job;

import com.dangdang.ddframe.job.lite.internal.instance.InstanceOperation;
import com.dangdang.ddframe.job.lite.internal.storage.JobNodePath;
import com.dangdang.ddframe.job.util.json.GsonFactory;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
@RestController
@SpringBootApplication
public class Application {

    @Autowired
    private ElasticJobService service;
    private final Map<String, ZooKeeper> movedSession = new HashMap<>();

    @GetMapping("/sessions")
    public Object sessions() {
        return service.getAllSession();
    }

    @GetMapping("/connections")
    public Object connections() {
        return service.getAllConnections();
    }

    @GetMapping("/move")
    public Object move(@RequestParam("from") String from, @RequestParam("to") String to) throws Exception {
        RegistryCenterProxy proxy = service.get(from);
        if (proxy == null) return movedSession;
        ZooKeeper zk = proxy.getZooKeeper();
        ZooKeeper moved = new ZooKeeper(to, 30000,
                event -> {
                },
                zk.getSessionId(),
                zk.getSessionPasswd());
        movedSession.put(from, moved);
        return movedSession;
    }

    @DeleteMapping("/move")
    public Object deleteMove(@RequestParam("from") String from) throws Exception {
        ZooKeeper moved = movedSession.remove(from);
        if (moved != null) {
            moved.close();
        }
        return movedSession;
    }

    @GetMapping("/moved")
    public Object moved(@RequestParam("server") String server) {
        RegistryCenterProxy proxy = service.get(server);
        if (proxy != null) {
            proxy.setSessionMoved(true);
            return proxy.toString();
        }
        return null;
    }

    @GetMapping("/recover")
    public Object recover(@RequestParam("server") String server, @RequestParam("updateNow") boolean updateNow) {
        RegistryCenterProxy proxy = service.get(server);
        if (proxy != null) {
            proxy.recoverFromSessionMoved(updateNow);
            return proxy.toString();
        }
        return null;
    }

    @GetMapping("/reconnect")
    public Object reconnect(@RequestParam("server") String server) {
        RegistryCenterProxy proxy = service.get(server);
        if (proxy != null) {
            proxy.reconnect();
            return proxy.toString();
        }
        return null;
    }

    @PostMapping("/config")
    public Object config(@RequestParam("job") String job) {
        JobNodePath jobNodePath = new JobNodePath(job.trim());
        RegistryCenterProxy proxy = service.getMaster();
        String config = proxy.get(jobNodePath.getConfigNodePath());
        Map<String, Object> configJson = GsonFactory.getGson().fromJson(config, Map.class);
        configJson.put("cron", cron(5));
        proxy.update(jobNodePath.getConfigNodePath(), GsonFactory.getGson().toJson(configJson));
        return jobNodePath;
    }

    public String cron(Integer wait) {
        LocalDateTime time = LocalDateTime.now().plusSeconds(wait == null ? 5 : wait);
        return String.format("%s %s %s %s %s ?", time.getSecond(), time.getMinute(), time.getHour(),
                time.getDayOfMonth(), time.getMonthValue());
    }

    @PostMapping("/trigger")
    public Object trigger(@RequestParam("job") String job) {
        JobNodePath jobNodePath = new JobNodePath(job.trim());
        RegistryCenterProxy proxy = service.getMaster();
        for (String each : proxy.getChildrenKeys(jobNodePath.getInstancesNodePath())) {
            proxy.persist(jobNodePath.getInstanceNodePath(each), InstanceOperation.TRIGGER.name());
        }
        return jobNodePath;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
