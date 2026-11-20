import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * 第 19 节演示 1：复现一个死锁，并用 JMX 在代码里检测出来
 *
 * 死锁成因：t1 按 A→B 的顺序加锁，t2 按 B→A 的顺序加锁，形成循环等待。
 *
 * 注意：两个线程设成了守护线程(daemon)，否则它们永远 BLOCKED，
 *      JVM 不会退出，这个程序就卡死在那里了 —— 而这正是线上死锁的真实表现。
 */
public class DeadlockDemo {

    private static final Object lockA = new Object();
    private static final Object lockB = new Object();

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== 1. 制造死锁 ===");

        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                System.out.println("  t1 拿到 lockA，想要 lockB");
                sleep(100);                       // 给对方抢锁的时间
                synchronized (lockB) {
                    System.out.println("  t1 拿到了 lockB（这行永远不会打印）");
                }
            }
        }, "deadlock-t1");

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {                // ⚠️ 加锁顺序和 t1 相反
                System.out.println("  t2 拿到 lockB，想要 lockA");
                sleep(100);
                synchronized (lockA) {
                    System.out.println("  t2 拿到了 lockA（这行永远不会打印）");
                }
            }
        }, "deadlock-t2");

        t1.setDaemon(true);      // 设成守护线程，让 JVM 最后能正常退出
        t2.setDaemon(true);
        t1.start();
        t2.start();

        Thread.sleep(600);       // 等死锁形成

        // ========== 2. 看线程状态 ==========
        System.out.println("\n=== 2. 线程状态 ===");
        System.out.println("  t1 状态 = " + t1.getState());
        System.out.println("  t2 状态 = " + t2.getState());
        System.out.println("  两个都是 BLOCKED，各自在等对方持有的锁");

        // ========== 3. 用 JMX 检测死锁 ==========
        System.out.println("\n=== 3. 代码内检测死锁（ThreadMXBean）===");
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] ids = bean.findDeadlockedThreads();

        if (ids == null) {
            System.out.println("  未检测到死锁");
        } else {
            System.out.println("  ⚠️ 检测到死锁，涉及 " + ids.length + " 个线程：");
            for (ThreadInfo info : bean.getThreadInfo(ids, true, true)) {
                System.out.println("    - 线程名: " + info.getThreadName());
                System.out.println("      状态: " + info.getThreadState());
                System.out.println("      正在等待的锁: " + info.getLockName());
                System.out.println("      该锁被谁持有: " + info.getLockOwnerName());
            }
        }

        // ========== 4. 排查方式说明 ==========
        System.out.println("\n=== 4. 线上怎么排查 ===");
        System.out.println("  当前进程 PID = " + ProcessHandle.current().pid());
        System.out.println("  可以在另一个终端执行：");
        System.out.println("      jstack " + ProcessHandle.current().pid() + " | grep -A 20 deadlock");
        System.out.println("  jstack 会直接打印 \"Found one Java-level deadlock\"");
        System.out.println("\n  这就是为什么要给线程起有意义的名字 ——");
        System.out.println("  如果叫 Thread-0 / Thread-1，你根本不知道是哪块业务代码。");

        System.out.println("\n=== 5. 死锁的四个必要条件 ===");
        System.out.println("  互斥      ：synchronized 保证一把锁同时只被一个线程持有  → 破坏不了");
        System.out.println("  持有并等待 ：拿着 lockA 的同时去等 lockB                 → 可破坏");
        System.out.println("  不可剥夺   ：synchronized 没超时机制，只能死等            → 用 tryLock 破坏");
        System.out.println("  循环等待   ：t1 等 t2，t2 等 t1                          → 固定加锁顺序破坏（最常用）");

        System.out.println("\n（程序即将退出。注意：如果上面两个线程不是守护线程，");
        System.out.println("  JVM 会永远卡在这里无法退出 —— 这就是线上死锁的真实表现）");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
