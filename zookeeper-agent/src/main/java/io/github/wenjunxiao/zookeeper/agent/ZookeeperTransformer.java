package io.github.wenjunxiao.zookeeper.agent;

import javassist.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

public class ZookeeperTransformer implements ClassFileTransformer {

    private ClassPool classPool = ClassPool.getDefault();
    private String sessionIdMask;

    public ZookeeperTransformer() {
        this.sessionIdMask = System.getenv("SESSION_ID_MASK");
    }

    private CtMethod overrideMethod(CtClass ctClass, String methodName) throws Exception {
        CtMethod ctMethod = ctClass.getDeclaredMethod(methodName);
        if (!ctClass.equals(ctMethod.getDeclaringClass())) {
            ctMethod = CtNewMethod.delegator(ctMethod, ctClass);
            ctClass.addMethod(ctMethod);
        }
        return ctMethod;
    }
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className.contains("org/apache/zookeeper/server/SessionTrackerImpl")) {
            try {
                classPool.importPackage(ZookeeperTransformer.class.getName());
                CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                CtMethod setOwner = overrideMethod(ctClass,"setOwner");
                System.out.println("========SessionTrackerImpl.setOwner===============");
                setOwner.insertBefore("System.out.println(\"========setOwner: 0x\"+Long.toHexString($1)+\"=>\"+$2);");
                if (sessionIdMask != null && !sessionIdMask.isEmpty()) {
                    System.out.println("========SessionTrackerImpl.initializeNextSession===============" + sessionIdMask);
                    CtMethod initializeNextSession = overrideMethod(ctClass,"initializeNextSession");
                    initializeNextSession.insertBefore("ZookeeperTransformer.waitForFileWhenLearnerSessionTracker($1);");
                    initializeNextSession.insertAfter("$_=$_&" + this.sessionIdMask + ";\n" +
                            "System.out.println(\"========initializeNextSession for \" + $1 + \" is 0x\" + Long.toHexString($_));");
                }
                System.out.println("========SessionTrackerImpl.done===============");
                return ctClass.toBytecode();
            } catch (Exception e) {
                System.err.println("========SessionTrackerImpl.error===============");
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return classfileBuffer;
    }

    public static void waitForFileWhenLearnerSessionTracker(long id) {
        boolean isLearnerSessionTracker = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().equals("org.apache.zookeeper.server.quorum.LearnerSessionTracker")) {
                isLearnerSessionTracker = true;
                break;
            }
        }
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        if (!isLearnerSessionTracker) {
            System.out.println(dateFormat.format(new Date()) + " ========waitForSignal[" + id + "] ignored");
            return;
        }
        File dir = new File(System.getenv("SESSION_SIGNAL_DIR"));
        if (!dir.exists()) {
            System.out.println("========create dirs: " + dir.getAbsolutePath() + " " + dir.mkdirs());
        }
        try {
            System.out.println(dateFormat.format(new Date()) + " ========waitForSignal[" + id + "] start => " + dir.getAbsolutePath());
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(dir.getAbsolutePath());
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            WatchKey watchKey = watchService.take();
            System.out.println(dateFormat.format(new Date()) + " ========waitForSignal[" + id + "] done: "
                    + watchKey.pollEvents().stream().map(event -> "<" + event.kind().name() + ">" + event.context())
                    .collect(Collectors.joining(",")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
