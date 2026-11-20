import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 第 21 节演示 1：虚拟线程基础（需要 JDK 21+）
 *
 * 编译运行：
 *   javac -encoding UTF-8 VirtualThreadDemo.java
 *   java VirtualThreadDemo
 */
public class VirtualThreadDemo {

    public static void main(String[] args) throws Exception {

        System.out.println("Java 版本 = " + System.getProperty("java.version"));
        System.out.println("CPU 核数  = " + Runtime.getRuntime().availableProcessors());

        // ========== 1. 创建一个虚拟线程 ==========
        System.out.println("\n=== 1. 创建虚拟线程 ===");
        Thread vt = Thread.ofVirtual().name("v-thread").start(() ->
                System.out.println("  虚拟线程内部看到的自己: " + Thread.currentThread()));
        vt.join();
        System.out.println("  isVirtual() = " + vt.isVirtual());
        System.out.println("  注意上面输出里的 @ForkJoinPool-1-worker-N —— 那是【载体线程】");
        System.out.println("  虚拟线程被挂在载体线程上执行，载体线程数 ≈ CPU 核数");

        // ========== 2. 平台线程 vs 虚拟线程 ==========
        System.out.println("\n=== 2. 平台线程对比 ===");
        Thread pt = Thread.ofPlatform().name("p-thread").start(() ->
                System.out.println("  平台线程内部看到的自己: " + Thread.currentThread()));
        pt.join();
        System.out.println("  isVirtual() = " + pt.isVirtual());

        // ========== 3. 10 万个虚拟线程 ==========
        System.out.println("\n=== 3. 10 万个虚拟线程，每个 sleep(100ms) ===");
        int n = 100_000;
        AtomicInteger done = new AtomicInteger();
        long start = System.currentTimeMillis();

        // ExecutorService 实现了 AutoCloseable，close() 会等所有任务完成
        try (ExecutorService es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                es.submit(() -> {
                    Thread.sleep(100);
                    done.incrementAndGet();
                    return null;
                });
            }
        }   // 这里会阻塞直到全部完成

        long cost = System.currentTimeMillis() - start;
        System.out.println("  完成 " + done.get() + " 个任务，总耗时 " + cost + " ms");
        System.out.println("  如果用 10 万个平台线程：10万 × 1MB 栈 = 100GB → 直接 OOM");

        // ========== 4. 不要池化虚拟线程 ==========
        System.out.println("\n=== 4. ⚠️ 不要池化虚拟线程 ===");
        System.out.println("  ❌ Executors.newFixedThreadPool(200, Thread.ofVirtual().factory())");
        System.out.println("     完全违背设计意图 —— 虚拟线程就是要\"用完即弃\"");
        System.out.println("  ✅ Executors.newVirtualThreadPerTaskExecutor()");

        // ========== 5. 限流要用 Semaphore，不能靠线程池大小 ==========
        System.out.println("\n=== 5. 虚拟线程下的限流 ===");
        demoRateLimit();

        // ========== 6. ThreadFactory 方式 ==========
        System.out.println("\n=== 6. 用 ThreadFactory 创建 ===");
        ThreadFactory factory = Thread.ofVirtual().name("task-", 1).factory();
        Thread t1 = factory.newThread(() ->
                System.out.println("  " + Thread.currentThread().getName()));
        Thread t2 = factory.newThread(() ->
                System.out.println("  " + Thread.currentThread().getName()));
        t1.start(); t1.join();
        t2.start(); t2.join();
        System.out.println("  名字自动编号，便于排查");
    }

    /** 虚拟线程没有数量限制，所以"用线程池大小限流"失效了，要用 Semaphore */
    private static void demoRateLimit() throws InterruptedException {
        Semaphore limiter = new Semaphore(5);          // 下游只能承受 5 并发
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        try (ExecutorService es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                es.submit(() -> {
                    limiter.acquire();                // 拿许可
                    try {
                        int now = inFlight.incrementAndGet();
                        maxInFlight.accumulateAndGet(now, Math::max);
                        Thread.sleep(20);             // 模拟调下游
                        inFlight.decrementAndGet();
                    } finally {
                        limiter.release();
                    }
                    return null;
                });
            }
        }

        System.out.println("  100 个虚拟线程 + Semaphore(5)");
        System.out.println("  实际最大并发 = " + maxInFlight.get() + "   ✅ 被限制在 5 以内");
        System.out.println("  （虚拟线程可以开 100 个，但下游压力由 Semaphore 控制）");
    }
}
