import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 第 21 节演示 2：虚拟线程 vs 平台线程，两种任务类型的对比
 *
 * 结论（跑完就能看到）：
 *   IO 密集型（阻塞等待）→ 虚拟线程快一个数量级
 *   CPU 密集型（纯计算）→ 虚拟线程没有优势，甚至略慢
 *
 * 注意：这个程序要跑十几秒，因为平台线程版本本身就慢。
 */
public class VirtualVsPlatformDemo {

    private static final int IO_TASKS = 20_000;     // IO 任务数
    private static final int IO_BLOCK_MS = 100;     // 每个任务阻塞时长
    private static final int CPU_TASKS = 2_000;     // CPU 任务数
    private static final int PLATFORM_POOL_SIZE = 200;

    public static void main(String[] args) throws Exception {

        System.out.println("CPU 核数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Java 版本 = " + System.getProperty("java.version"));

        // ============ 场景一：IO 密集型 ============
        System.out.println("\n========== 场景一：IO 密集型（每个任务阻塞 "
                + IO_BLOCK_MS + "ms，共 " + IO_TASKS + " 个）==========");

        long vIo = runVirtual(IO_TASKS, VirtualVsPlatformDemo::ioTask);
        System.out.println("  虚拟线程（每任务一个）        : " + vIo + " ms");

        long pIo = runPlatform(IO_TASKS, VirtualVsPlatformDemo::ioTask);
        System.out.println("  平台线程池（" + PLATFORM_POOL_SIZE + " 个线程）    : " + pIo + " ms");

        System.out.printf("  → 虚拟线程快约 %.1f 倍%n", pIo * 1.0 / Math.max(vIo, 1));
        System.out.println("  理论值：平台线程需要 " + IO_TASKS + "/" + PLATFORM_POOL_SIZE
                + " = " + (IO_TASKS / PLATFORM_POOL_SIZE) + " 轮 × " + IO_BLOCK_MS
                + "ms ≈ " + (IO_TASKS / PLATFORM_POOL_SIZE * IO_BLOCK_MS) + " ms");

        // ============ 场景二：CPU 密集型 ============
        System.out.println("\n========== 场景二：CPU 密集型（纯计算，共 "
                + CPU_TASKS + " 个）==========");

        long vCpu = runVirtual(CPU_TASKS, VirtualVsPlatformDemo::cpuTask);
        System.out.println("  虚拟线程（每任务一个）        : " + vCpu + " ms");

        int cores = Runtime.getRuntime().availableProcessors();
        long pCpu = runFixedPlatform(CPU_TASKS, cores + 1, VirtualVsPlatformDemo::cpuTask);
        System.out.println("  平台线程池（核数+1 = " + (cores + 1) + " 个线程）: " + pCpu + " ms");

        System.out.println("  → 两者接近，虚拟线程没有优势");
        System.out.println("    原因：计算任务不阻塞，虚拟线程没有\"卸下让位\"的机会，");
        System.out.println("          底层载体线程数就是 CPU 核数，并行度和平台线程池一样。");

        System.out.println("\n========== 结论 ==========");
        System.out.println("  IO 密集（DB / HTTP / 文件）→ 虚拟线程，不要池化");
        System.out.println("  CPU 密集（加密 / 压缩 / 计算）→ 固定大小的平台线程池（核数+1）");
    }

    /** 每个任务一个虚拟线程 */
    private static long runVirtual(int tasks, Runnable task) throws Exception {
        AtomicInteger done = new AtomicInteger();
        long start = System.currentTimeMillis();
        try (ExecutorService es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                es.submit(() -> {
                    task.run();
                    done.incrementAndGet();
                });
            }
        }
        long cost = System.currentTimeMillis() - start;
        if (done.get() != tasks) {
            System.out.println("  ⚠️ 只完成了 " + done.get() + "/" + tasks);
        }
        return cost;
    }

    /** 固定 200 个平台线程 */
    private static long runPlatform(int tasks, Runnable task) throws Exception {
        return runFixedPlatform(tasks, PLATFORM_POOL_SIZE, task);
    }

    private static long runFixedPlatform(int tasks, int poolSize, Runnable task)
            throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                poolSize, poolSize, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(tasks + 10));

        AtomicInteger done = new AtomicInteger();
        long start = System.currentTimeMillis();
        for (int i = 0; i < tasks; i++) {
            pool.execute(() -> {
                task.run();
                done.incrementAndGet();
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.MINUTES);
        long cost = System.currentTimeMillis() - start;
        if (done.get() != tasks) {
            System.out.println("  ⚠️ 只完成了 " + done.get() + "/" + tasks);
        }
        return cost;
    }

    /** IO 任务：单纯阻塞等待，模拟数据库/HTTP 调用 */
    private static void ioTask() {
        try {
            Thread.sleep(IO_BLOCK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final AtomicLong SINK = new AtomicLong();

    /** CPU 任务：纯计算，不阻塞 */
    private static void cpuTask() {
        long sum = 0;
        for (int i = 0; i < 2_000_000; i++) {
            sum += (long) Math.sqrt(i) + (i % 7);
        }
        SINK.addAndGet(sum);          // 防止 JIT 把计算优化掉
    }
}
