import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示：四种内置拒绝策略 + 一个自定义策略
 *
 * 统一参数：核心 1，最大 1，队列容量 1  →  最大承载 2 个任务
 * 每次都提交 4 个任务（每个 500 ms），所以第 3、4 个必然触发拒绝策略。
 */
public class RejectPolicyDemo {

    public static void main(String[] args) throws InterruptedException {
        test("AbortPolicy（默认）",       new ThreadPoolExecutor.AbortPolicy());
        test("CallerRunsPolicy",         new ThreadPoolExecutor.CallerRunsPolicy());
        test("DiscardPolicy",            new ThreadPoolExecutor.DiscardPolicy());
        test("DiscardOldestPolicy",      new ThreadPoolExecutor.DiscardOldestPolicy());
        test("自定义策略（记日志+降级）",   new MyRejectHandler());
    }

    private static void test(String name, RejectedExecutionHandler handler)
            throws InterruptedException {

        System.out.println("\n================ " + name + " ================");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                handler);

        for (int i = 1; i <= 4; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    System.out.printf("    任务%d 执行中，线程 = %s%n",
                            taskId, Thread.currentThread().getName());
                    sleep(500);
                });
                System.out.printf("  提交任务%d 成功%n", taskId);
            } catch (RejectedExecutionException e) {
                System.out.printf("  提交任务%d 抛出 %s%n",
                        taskId, e.getClass().getSimpleName());
            }
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  本轮实际完成任务数 = " + pool.getCompletedTaskCount());
    }

    /** 自定义拒绝策略：记录日志 + 统计 + 降级处理 */
    static class MyRejectHandler implements RejectedExecutionHandler {
        private final AtomicInteger rejectedCount = new AtomicInteger();

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            int total = rejectedCount.incrementAndGet();
            System.out.printf("  [告警] 线程池已满！累计拒绝 %d 次 "
                            + "(活跃=%d, 队列=%d, 已完成=%d)%n",
                    total, executor.getActiveCount(),
                    executor.getQueue().size(), executor.getCompletedTaskCount());
            // 真实项目在这里做降级：落 MQ 异步重试 / 写库待补偿 / 上报监控
            System.out.println("  [降级] 任务已转入补偿队列（此处仅打印示意）");
        }
    }

    /** 把 InterruptedException 包一层，Lambda 里用起来干净些 */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
