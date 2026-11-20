import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级线程池模板，可直接复制到项目使用。包含：
 *   - 手动 new ThreadPoolExecutor（不用 Executors）
 *   - 有界队列
 *   - 带业务含义的线程名 + 未捕获异常处理器
 *   - CallerRunsPolicy（不丢任务 + 自带反压）
 *   - 监控方法 stats()
 *   - 两阶段优雅关闭
 */
public final class OrderThreadPool {

    private static final int CPU = Runtime.getRuntime().availableProcessors();

    /** 队列容量单独提出来，监控时要用 */
    private static final int QUEUE_CAPACITY = 500;

    public static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            CPU * 2,                                   // 核心线程：IO 密集型取 2 倍核数
            CPU * 4,                                   // 最大线程
            60L, TimeUnit.SECONDS,                     // 空闲 60 秒回收非核心线程
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),  // 有界队列，挡住流量
            namedFactory("order-async"),               // 有业务含义的线程名
            new ThreadPoolExecutor.CallerRunsPolicy()  // 不丢任务 + 自带反压
    );

    private OrderThreadPool() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 自定义 ThreadFactory：统一命名 + 兜住未捕获异常 */
    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(false);   // 业务线程池用非守护线程，保证能优雅关闭
            t.setUncaughtExceptionHandler((thread, e) ->
                    System.err.printf("[%s] 未捕获异常: %s%n", thread.getName(), e));
            return t;
        };
    }

    /** 监控：这四个指标能发现 90% 的线程池问题 */
    public static String stats() {
        return String.format(
                "线程池状态 -> 活跃=%d/%d, 队列=%d/%d, 已完成=%d, 总任务=%d",
                POOL.getActiveCount(), POOL.getMaximumPoolSize(),
                POOL.getQueue().size(), QUEUE_CAPACITY,
                POOL.getCompletedTaskCount(), POOL.getTaskCount());
    }

    /** 两阶段优雅关闭 */
    public static void shutdownGracefully() {
        POOL.shutdown();                                        // 1. 不再接新任务
        try {
            if (!POOL.awaitTermination(30, TimeUnit.SECONDS)) { // 2. 等已有任务跑完
                System.out.println("30 秒内未结束，强制中断剩余任务");
                POOL.shutdownNow();                             // 3. 超时则中断
                POOL.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            POOL.shutdownNow();
            Thread.currentThread().interrupt();                 // 恢复中断标志
        }
    }
}
