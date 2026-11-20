import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 第 18 节演示 3：Semaphore —— 限制同时访问的数量
 *
 * 场景：第三方接口只能承受 3 个并发，但我们有 10 个线程要调它。
 */
public class SemaphoreDemo {

    /** 只发 3 个许可 */
    private static final Semaphore SEMAPHORE = new Semaphore(3);

    /** 用来观察"同时有几个线程在临界区里" */
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static final AtomicInteger maxInFlight = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                10, 10, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));

        // ========== 1. acquire/release 基本用法 ==========
        System.out.println("=== 1. 10 个线程抢 3 个许可 ===");
        for (int i = 1; i <= 10; i++) {
            final int id = i;
            pool.execute(() -> callThirdPartyApi(id));
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n  整个过程中同时在临界区的线程数最大值 = " + maxInFlight.get());
        System.out.println("  ✅ 始终不超过许可数 3");
        System.out.println("  当前可用许可 = " + SEMAPHORE.availablePermits() + "（全部归还了）");

        // ========== 2. tryAcquire 带超时：快速失败 ==========
        System.out.println("\n=== 2. tryAcquire 带超时（更实用）===");
        Semaphore s2 = new Semaphore(1);
        s2.acquire();                                   // 先把唯一的许可占掉
        System.out.println("  许可已被占用，可用许可 = " + s2.availablePermits());

        boolean got = s2.tryAcquire(300, TimeUnit.MILLISECONDS);
        System.out.println("  tryAcquire(300ms) 返回 = " + got
                + "   ← 拿不到就快速失败，不会无限等");
        if (!got) {
            System.out.println("  业务上应该在这里抛 ServiceBusyException 或走降级");
        }
        s2.release();

        // ========== 3. Semaphore(1) 不是可重入锁 ==========
        System.out.println("\n=== 3. ⚠️ Semaphore(1) 和锁的区别：不可重入 ===");
        Semaphore s3 = new Semaphore(1);
        s3.acquire();
        System.out.println("  第一次 acquire 成功");
        boolean second = s3.tryAcquire(200, TimeUnit.MILLISECONDS);
        System.out.println("  同一个线程再 acquire = " + second
                + "   ← 不可重入！ReentrantLock 在这里会成功");
        s3.release();

        System.out.println("\n=== 典型用途 ===");
        System.out.println("  · 限制对下游的并发调用数（对方只能承受 N 并发）");
        System.out.println("  · 简单的连接池 / 资源池");
        System.out.println("  · 单机限流");
        System.out.println("  · 虚拟线程场景下的限流（线程池大小不再能限流，见第 21 节）");
    }

    /** 模拟调用第三方接口，用信号量把并发压到 3 以内 */
    private static void callThirdPartyApi(int taskId) {
        try {
            SEMAPHORE.acquire();                        // 拿许可，没有就阻塞
            try {
                int now = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(now, Math::max);
                System.out.println("  任务" + taskId + " 拿到许可，当前并发 = " + now
                        + "，剩余许可 = " + SEMAPHORE.availablePermits());
                Thread.sleep(300);                      // 模拟接口耗时
                inFlight.decrementAndGet();
            } finally {
                SEMAPHORE.release();                    // 必须在 finally 里归还
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
