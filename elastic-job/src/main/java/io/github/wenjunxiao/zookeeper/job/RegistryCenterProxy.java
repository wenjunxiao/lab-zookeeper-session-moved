package io.github.wenjunxiao.zookeeper.job;

import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;
import com.dangdang.ddframe.job.reg.exception.RegException;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperConfiguration;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter;
import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

public class RegistryCenterProxy implements CoordinatorRegistryCenter {
    private final Logger log = LoggerFactory.getLogger(RegistryCenterProxy.class);
    private final ZookeeperRegistryCenter regCenter;
    private boolean sessionMoved = false;

    public RegistryCenterProxy(String serverLists, String namespace) {
        regCenter = new ZookeeperRegistryCenter(new ZookeeperConfiguration(serverLists, namespace));
    }

    public <T> T detectException(Supplier<T> action) {
        try {
            return action.get();
        } catch (RegException e) {
            if (e.getCause() instanceof KeeperException.SessionMovedException) {
                sessionMoved = true;
            }
            throw e;
        }
    }

    public boolean isSessionMoved() {
        return sessionMoved;
    }

    public void recoverFromSessionMoved() {
        CuratorFramework framework = ((CuratorFramework) regCenter.getRawClient());
        CuratorZookeeperClient zookeeperClient = framework.getZookeeperClient();
        try {
            ZooKeeper zooKeeper = zookeeperClient.getZooKeeper();
            zooKeeper.close();
            sessionMoved = false;
        } catch (Exception e) {
            log.error("recover error", e);
        }
    }

    @Override
    public String getDirectly(String key) {
        return regCenter.getDirectly(key);
    }

    @Override
    public List<String> getChildrenKeys(String key) {
        return regCenter.getChildrenKeys(key);
    }

    @Override
    public int getNumChildren(String key) {
        return regCenter.getNumChildren(key);
    }

    @Override
    public void persistEphemeral(String key, String value) {
        detectException((Supplier<Void>) () -> {
            regCenter.persistEphemeral(key, value);
            return null;
        });
    }

    @Override
    public String persistSequential(String key, String value) {
        return detectException(() -> regCenter.persistSequential(key, value));
    }

    @Override
    public void persistEphemeralSequential(String key) {
        detectException((Supplier<Void>) () -> {
            regCenter.persistEphemeralSequential(key);
            return null;
        });
    }

    @Override
    public void addCacheData(String cachePath) {
        regCenter.addCacheData(cachePath);
    }

    @Override
    public void evictCacheData(String cachePath) {
        regCenter.evictCacheData(cachePath);
    }

    @Override
    public Object getRawCache(String cachePath) {
        return regCenter.getRawCache(cachePath);
    }

    @Override
    public void init() {
        regCenter.init();
    }

    @Override
    public void close() {
        regCenter.close();
    }

    @Override
    public String get(String key) {
        return regCenter.get(key);
    }

    @Override
    public boolean isExisted(String key) {
        return regCenter.isExisted(key);
    }

    @Override
    public void persist(String key, String value) {
        detectException((Supplier<Void>) () -> {
            regCenter.persist(key, value);
            return null;
        });
    }

    @Override
    public void update(String key, String value) {
        detectException((Supplier<Void>) () -> {
            regCenter.update(key, value);
            return null;
        });
    }

    @Override
    public void remove(String key) {
        detectException((Supplier<Void>) () -> {
            regCenter.remove(key);
            return null;
        });
    }

    @Override
    public long getRegistryCenterTime(String key) {
        return regCenter.getRegistryCenterTime(key);
    }

    @Override
    public Object getRawClient() {
        return regCenter.getRawClient();
    }

    public static String getRegCenterDesc(CoordinatorRegistryCenter regCenter) {
        CuratorFramework framework = ((CuratorFramework) regCenter.getRawClient());
        CuratorZookeeperClient zookeeperClient = framework.getZookeeperClient();
        try {
            ZooKeeper zooKeeper = zookeeperClient.getZooKeeper();
            return framework.getNamespace() + "@" + zookeeperClient.getCurrentConnectionString() + "(" + zooKeeper.toString() + ")";
        } catch (Exception e) {
            return framework.getNamespace() + "@" + zookeeperClient.getCurrentConnectionString();
        }
    }

    public ZooKeeper getZooKeeper() {
        CuratorFramework framework = ((CuratorFramework) regCenter.getRawClient());
        CuratorZookeeperClient zookeeperClient = framework.getZookeeperClient();
        try {
            return zookeeperClient.getZooKeeper();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "RegistryCenterProxy{" + getRegCenterDesc(regCenter) + '}';
    }
}
