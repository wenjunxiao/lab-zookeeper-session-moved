# Zookeeper Session Moved

当前测试版本`3.4.5`出现`SessionMoved`问题按照官方
[ZOOKEEPER-710](https://issues.apache.org/jira/browse/ZOOKEEPER-710) 及其
[修复patch](https://issues.apache.org/jira/secure/attachment/12439239/ZOOKEEPER-710_3.3.patch),
[ZOOKEEPER-2886](https://issues.apache.org/jira/browse/ZOOKEEPER-2886) 及其
[修复patch](https://patch-diff.githubusercontent.com/raw/apache/zookeeper/pull/353.patch),
根据修复情况以及测试Case看出，构造`SessionMoved`的场景都是同一个Session连接到其他节点了
```java
ZooKeeper zknew = new ZooKeeper(hostPorts[1],
    ClientBase.CONNECTION_TIMEOUT,
    new Watcher() {public void process(WatchedEvent event) {}},
    zk.getSessionId(),
    zk.getSessionPasswd());
```
但目前遇到的场景是，如果连接的地址中只指定其中一个节点也会出现`SessionMoved`的问题，而且出现异常之后查看各个服务器节点，
在其他节点并未发现相同的`SessionId`，查看出现`SessionMoved`的代码主要是在类`org.apache.zookeeper.server.SessionTrackerImpl`中
的`setOwner`如果一旦修改了某个`sessionId`的Owner，则会导致在`checkSession`时触发异常。也就是说只要其他节点出现了相同ID则会有异常。
出现ID重复的情况有两种，一种节点的连接重连到其他节点，另一种其他节点分配了相同ID。由于在测试时只指定了一个服务器节点，因此不会出现重连到其他节点的情况，
也就是说应该时其他节点分配了相同的ID，查看ID分配生成的代码
```java
public static long initializeNextSession(long id) {
    long nextSid = 0;
    nextSid = (System.currentTimeMillis() << 24) >> 8;
    nextSid =  nextSid | (id <<56);
    return nextSid;
}
```
看起来ID的规则是`8位的服务节点ID + 40位时间戳 + 16位的自增序号`，不过这里注意到在对时间戳进行右移用的是算术右移`>>`（该问题已经在[ZOOKEEPER-1622](https://issues.apache.org/jira/browse/ZOOKEEPER-1622) 中修复），
这会导致当时间戳左移24位后高位是1时，右移的后的高位全是1，进而导致下一行想在高位填充服务节点ID时无效。也就是说如果所有节点同时启动，
所有节点的初始会话ID都是一样，这导致接下来各个节点分配的会话会出现大量相同的ID，进而触发`SessionMoved`异常，在修复版本中会导致
会话在进行操作时进行重连（由于使用相同会话ID，因此如果另外节点的也进行操作，那么Owner就会来回切换）。即使在节点启动存在秒级启动时间差时，
差异的有效位数也仅有`16+12=28`即最大2亿个连接之后开始重复。

查看了测试集群中各个节点最小和最大的会话ID如下：

| Node  |   MinSessionId     |    MaxSessionId    |
|-------|--------------------|--------------------|
| zk001 | 0xff86da4e1100098f | 0xff86da4e11048a16 |
| zk002 | 0xff86da4e11000e8d | 0xff86da4e11014676 |
| zk003 | 0xff86da4e110047b2 | 0xff86da4e110482dc |
| zk004 | 0xff86da4e1100239e | 0xff86da4e110281cc |
| zk005 | 0xff86da4e1101179e | 0xff86da4e110149bf |

都有共同的`0xff86da4e110`，也就是说只有`16+4=20`位(服务节点之间只有毫秒级启动差)，而且从最大值可以看出，`zk001`节点的会话ID极有可能被`zk003`节点的会话ID覆盖，
`zk005`节点的会话ID极有可能被`zk002`节点的会话ID覆盖。

当然出现`SessionMoved`在官方给出的解决方案是断开重连，因此如果一种是升级服务器版本到修复版本，另一种就是在客户端捕获异常重连

测试例子`bin/zkServerCluster.cmd`，为了本地模拟同时启动控制通过Agent重置了返回的初始会话ID，
然后启动`elastic-job`应用，之后执行
```bash
curl -X POST "http://127.0.0.1:8080/trigger?job=io.github.wenjunxiao.zookeeper.job.ElasticTestJob1"
```
可以看到日志正是通过Trigger触发任务执行的代码
```log
com.dangdang.ddframe.job.reg.exception.RegException: org.apache.zookeeper.KeeperException$SessionMovedException: KeeperErrorCode = Session moved
	at com.dangdang.ddframe.job.reg.exception.RegExceptionHandler.handleException(RegExceptionHandler.java:52) ~[elastic-job-common-core-2.1.5.jar:na]
	at com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter.update(ZookeeperRegistryCenter.java:236) ~[elastic-job-common-core-2.1.5.jar:na]
	at io.github.wenjunxiao.zookeeper.job.RegistryCenterProxy.lambda$update$4(RegistryCenterProxy.java:134) ~[classes/:na]
	at io.github.wenjunxiao.zookeeper.job.RegistryCenterProxy.detectException(RegistryCenterProxy.java:28) ~[classes/:na]
	at io.github.wenjunxiao.zookeeper.job.RegistryCenterProxy.update(RegistryCenterProxy.java:133) ~[classes/:na]
	at com.dangdang.ddframe.job.lite.internal.storage.JobNodeStorage.updateJobNode(JobNodeStorage.java:150) ~[elastic-job-lite-core-2.1.5.jar:na]
	at com.dangdang.ddframe.job.lite.internal.instance.InstanceService.clearTriggerFlag(InstanceService.java:65) ~[elastic-job-lite-core-2.1.5.jar:na]
	at com.dangdang.ddframe.job.lite.internal.instance.TriggerListenerManager$JobTriggerStatusJobListener.dataChanged(TriggerListenerManager.java:58) ~[elastic-job-lite-core-2.1.5.jar:na]
	at com.dangdang.ddframe.job.lite.internal.listener.AbstractJobListener.childEvent(AbstractJobListener.java:44) ~[elastic-job-lite-core-2.1.5.jar:na]
	at org.apache.curator.framework.recipes.cache.TreeCache$2.apply(TreeCache.java:732) [curator-recipes-2.10.0.jar:na]
	at org.apache.curator.framework.recipes.cache.TreeCache$2.apply(TreeCache.java:726) [curator-recipes-2.10.0.jar:na]
	at org.apache.curator.framework.listen.ListenerContainer$1.run(ListenerContainer.java:93) [curator-framework-2.10.0.jar:na]
	at com.google.common.util.concurrent.MoreExecutors$DirectExecutorService.execute(MoreExecutors.java:299) [guava-18.0.jar:na]
	at org.apache.curator.framework.listen.ListenerContainer.forEach(ListenerContainer.java:85) [curator-framework-2.10.0.jar:na]
	at org.apache.curator.framework.recipes.cache.TreeCache.callListeners(TreeCache.java:725) [curator-recipes-2.10.0.jar:na]
	at org.apache.curator.framework.recipes.cache.TreeCache.access$1400(TreeCache.java:71) [curator-recipes-2.10.0.jar:na]
	at org.apache.curator.framework.recipes.cache.TreeCache$4.run(TreeCache.java:843) [curator-recipes-2.10.0.jar:na]
	at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511) [na:1.8.0_202]
	at java.util.concurrent.FutureTask.run(FutureTask.java:266) [na:1.8.0_202]
	at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511) [na:1.8.0_202]
	at java.util.concurrent.FutureTask.run(FutureTask.java:266) [na:1.8.0_202]
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149) [na:1.8.0_202]
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624) [na:1.8.0_202]
	at java.lang.Thread.run(Thread.java:748) [na:1.8.0_202]
```
通过更新cron来执行任务
```bash
curl -X POST "http://127.0.0.1:8080/config?job=io.github.wenjunxiao.zookeeper.job.ElasticTestJob1"
```
则会看到一下日志
```log
[stJob1_Worker-1] c.d.d.j.l.i.sharding.ShardingService     : Job 'io.github.wenjunxiao.zookeeper.job.ElasticTestJob1' sharding begin.
[127.0.0.1:2181)] org.apache.zookeeper.ClientCnxn          : Unable to read additional data from server sessionid 0xff8b0636a0000000, likely server has closed socket, closing socket connection and attempting reconnect
[ain-EventThread] o.a.c.f.state.ConnectionStateManager     : State change: SUSPENDED
[127.0.0.1:2181)] org.apache.zookeeper.ClientCnxn          : Opening socket connection to server 127.0.0.1/127.0.0.1:2181. Will not attempt to authenticate using SASL (unknown error)
[127.0.0.1:2181)] org.apache.zookeeper.ClientCnxn          : Socket connection established to 127.0.0.1/127.0.0.1:2181, initiating session
[127.0.0.1:2181)] org.apache.zookeeper.ClientCnxn          : Session establishment complete on server 127.0.0.1/127.0.0.1:2181, sessionid = 0xff8b0636a0000000, negotiated timeout = 40000
[ain-EventThread] o.a.c.f.state.ConnectionStateManager     : State change: RECONNECTED
[stJob1_Worker-1] c.d.d.j.l.i.sharding.ShardingService     : Job 'io.github.wenjunxiao.zookeeper.job.ElasticTestJob1' sharding complete.
```
通过以上两个操作可以看到当前版本中，`create`/`remove`等操作会触发服务端断开连接而重连，而`multi`操作则会抛出异常`SessionMoved`，
因此大部分情况下不会影响`elastic-job`的启动和正常执行，以及通过修改CRON来执行任务，但是会影响通过Trigger触发任务，因为涉及到`multi`操作的只有
`com.dangdang.ddframe.job.lite.internal.instance.InstanceService#clearTriggerFlag`是在启动后执行的，其他`multi`操作都是在启动过程中执行，
除非两个不同节点的任务同时启动并且分配了相同的会话ID才会导致启动过程中失败。

zookeeper同时解决`SessionMoved`(`3.6`)和`会话ID`(`3.4.6`, `3.5.0`)问题的最低版本是`3.6`， 但是`elastic-job`的`2.1.5`不支持zookeeper的`3.6`，
因此一种是升级到`3.4.6`解决会话ID的问题来降低出现`SessionMoved`的概率。