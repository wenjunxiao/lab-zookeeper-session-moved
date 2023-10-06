package io.github.wenjunxiao.zookeeper.job;

public class SessionId {

    public static void main(String[] args) {
        long time = 1696606883859L; // System.currentTimeMillis();
        sessionId1(time);
        sessionId2(time);
        sessionId1(999L);
        System.out.println(Long.toHexString(999L));
    }

    public static void sessionId1(long time) {
        System.out.println("======sessionId1.start======");
        System.out.println(time);
        System.out.println(toHexString(time));                                     // 0x0000018b05a69013
        System.out.println(toHexString(time << 24));                            // 0x8b05a69013000000
        System.out.println(toHexString((time << 24) >> 8));                     // 0xff8b05a690130000
        System.out.println(toHexString(1L << 56));                              // 0x0100000000000000
        System.out.println(toHexString(((time << 24) >> 8) | 1L << 56));          // 0xff8b05a690130000
        System.out.println("======sessionId1.end======");
    }

    public static void sessionId2(long time) {
        System.out.println("======sessionId2.start======");
        System.out.println(time);
        System.out.println(toHexString(time));                                        // 0x0000018b05a69013
        System.out.println(toHexString(time << 24));                            // 0x8b05a69013000000
        System.out.println(toHexString((time << 24) >>> 8));                    // 0xff8b05a690130000
        System.out.println(toHexString(1L << 56));                              // 0x0100000000000000
        System.out.println(toHexString(((time << 24) >>> 8) | 1L << 56));       // 0x018b05a690130000
        System.out.println("======sessionId2.end======");
    }

    private static String toHexString(long value) {
        return String.format("0x%016x", value);
    }
}
