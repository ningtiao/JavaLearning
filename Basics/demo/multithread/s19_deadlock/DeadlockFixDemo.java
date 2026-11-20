import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 第 19 节演示 2：转账死锁的两种修复方案
 *
 * 场景：线程1 执行 transfer(A, B)，线程2 同时执行 transfer(B, A)
 *      按参数顺序加锁 → 必然死锁
 *
 * 修复一：按账户 ID 排序加锁（破坏"循环等待"）—— 最常用
 * 修复二：tryLock 带超时 + 随机退避（破坏"不可剥夺"）
 */
public class DeadlockFixDemo {

    /** 账户：同时准备了 synchronized 用的对象锁和 ReentrantLock */
    static class Account {
        final int id;
        final String name;
        long balance;
        final ReentrantLock lock = new ReentrantLock();

        Account(int id, String name, long balance) {
            this.id = id;
            this.name = name;
            this.balance = balance;
        }

        void debit(long amount)  { balance -= amount; }
        void credit(long amount) { balance += amount; }

        @Override public String toString() {
            return name + "(余额=" + balance + ")";
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // ========== 修复一：固定加锁顺序 ==========
        System.out.println("=== 修复一：按账户 ID 排序加锁（破坏循环等待）===");
        runConcurrentTransfer((from, to, amount) -> {
            // 关键：不按参数顺序，而是按一个全局一致的顺序（这里用 id）
            Account first  = from.id < to.id ? from : to;
            Account second = from.id < to.id ? to : from;

            synchronized (first) {
                synchronized (second) {
                    from.debit(amount);
                    to.credit(amount);
                }
            }
        });

        // ========== 修复二：tryLock 带超时 ==========
        System.out.println("\n=== 修复二：tryLock 带超时 + 随机退避（破坏不可剥夺）===");
        runConcurrentTransfer((from, to, amount) -> {
            while (true) {
                try {
                    if (from.lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                        try {
                            if (to.lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                                try {
                                    from.debit(amount);
                                    to.credit(amount);
                                    return;                   // 成功，退出重试循环
                                } finally {
                                    to.lock.unlock();
                                }
                            }
                        } finally {
                            from.lock.unlock();                // 拿不到第二把就释放第一把
                        }
                    }
                    // 随机退避：避免两个线程同步重试，陷入"活锁"
                    Thread.sleep(ThreadLocalRandom.current().nextInt(20));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        System.out.println("\n=== 对比：不做任何修复会怎样 ===");
        System.out.println("  如果直接写 synchronized(from) { synchronized(to) {...} }，");
        System.out.println("  线程1 transfer(A,B) 锁 A→B，线程2 transfer(B,A) 锁 B→A，");
        System.out.println("  两个线程各持一把、各等一把 → 死锁（见 DeadlockDemo）");
    }

    /** 转账逻辑的抽象，方便替换不同实现 */
    interface TransferStrategy {
        void transfer(Account from, Account to, long amount);
    }

    /**
     * 并发压测：两个方向互相转账各 2000 次。
     * 如果实现有死锁风险，这里几乎必然卡住。
     */
    private static void runConcurrentTransfer(TransferStrategy strategy)
            throws InterruptedException {

        Account a = new Account(1, "账户A", 100_000);
        Account b = new Account(2, "账户B", 100_000);
        long totalBefore = a.balance + b.balance;

        int rounds = 2000;
        CountDownLatch done = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < rounds; i++) strategy.transfer(a, b, 10);
            done.countDown();
        }, "transfer-A-to-B");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < rounds; i++) strategy.transfer(b, a, 10);   // 反方向！
            done.countDown();
        }, "transfer-B-to-A");

        t1.setDaemon(true);
        t2.setDaemon(true);
        long start = System.currentTimeMillis();
        t1.start();
        t2.start();

        boolean finished = done.await(15, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - start;

        if (!finished) {
            System.out.println("  ❌ 15 秒内没跑完，疑似死锁！");
            long[] ids = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
            System.out.println("  JMX 死锁检测: "
                    + (ids == null ? "未检测到" : "检测到 " + ids.length + " 个线程死锁"));
            return;
        }

        System.out.println("  ✅ 两个方向各转账 " + rounds + " 次，无死锁，耗时 " + cost + " ms");
        System.out.println("     " + a + "  " + b);
        System.out.println("     转账前总额 = " + totalBefore
                + "，转账后总额 = " + (a.balance + b.balance)
                + (totalBefore == a.balance + b.balance ? "  （守恒 ✅）" : "  （不守恒 ❌）"));
    }
}
