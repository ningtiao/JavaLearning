import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：线程池任务提交的完整流程
 *
 * 参数：核心 2，最大 4，队列容量 2  →  最大承载 4 + 2 = 6 个任务
 *
 * 每个任务耗时 1000 ms，两次提交之间间隔 100 ms，
 * 这样输出顺序是确定的，方便对照流程图阅读。
 */
public class ThreadPoolFlowDemo {

    public static void main(String[] args) throws InterruptedException {

        System.out.println("CPU 核数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("参数：核心 2，最大 4，队列容量 2 → 最大承载 6 个任务");
        System.out.println("--------------------------------------------------------");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,                                       // corePoolSize
                4,                                       // maximumPoolSize
                10L, TimeUnit.SECONDS,                   // keepAliveTime
                new ArrayBlockingQueue<>(2),             // workQueue 容量 2
                new ThreadPoolExecutor.AbortPolicy());   // 拒绝策略：抛异常

        for (int i = 1; i <= 7; i++) {
            final int taskId = i;                        // Lambda 里要用，必须是 final
            try {
                pool.execute(() -> runTask(taskId));
                System.out.printf("提交任务%d 成功    线程数=%d, 队列中=%d%n",
                        taskId, pool.getPoolSize(), pool.getQueue().size());
            } catch (RejectedExecutionException e) {
                System.out.printf("提交任务%d 被拒绝  -> %s    线程数=%d, 队列中=%d%n",
                        taskId, e.getClass().getSimpleName(),
                        pool.getPoolSize(), pool.getQueue().size());
            }
            Thread.sleep(100);   // 放慢提交速度，让输出顺序稳定
        }

        System.out.println("--------------------------------------------------------");
        pool.shutdown();
        boolean done = pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("所有任务已结束 = " + done);
        System.out.println("已完成任务数   = " + pool.getCompletedTaskCount());
    }

    /** 模拟一个耗时 1 秒的业务任务 */
    private static void runTask(int taskId) {
        System.out.printf("    >>> 任务%d 开始，执行线程 = %s%n",
                taskId, Thread.currentThread().getName());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 恢复中断标志，不要吞掉
            return;
        }
        System.out.printf("    <<< 任务%d 结束%n", taskId);
    }
}
