import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：shutdown() 与 shutdownNow() 的区别
 *
 * 两次都是：1 个线程、队列容量 10、提交 5 个任务（每个 300 ms），
 * 等第一个任务跑起来后再关闭，对比队列里剩下 4 个任务的命运。
 */
public class ShutdownDemo {

    public static void main(String[] args) throws InterruptedException {
        demo(false);   // shutdown()
        demo(true);    // shutdownNow()
    }

    private static void demo(boolean useShutdownNow) throws InterruptedException {

        String name = useShutdownNow ? "shutdownNow()" : "shutdown()";
        System.out.println("\n================ " + name + " ================");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10));

        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("    任务" + taskId + " 开始");
                try {
                    Thread.sleep(300);
                    System.out.println("    任务" + taskId + " 正常结束");
                } catch (InterruptedException e) {
                    System.out.println("    任务" + taskId + " 被中断！");
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(100);   // 等任务1 跑起来
        System.out.println("  关闭前：队列中还有 " + pool.getQueue().size() + " 个任务");

        if (useShutdownNow) {
            List<Runnable> notRun = pool.shutdownNow();
            System.out.println("  shutdownNow() 返回了 " + notRun.size()
                    + " 个未执行的任务（被丢弃）");
        } else {
            pool.shutdown();
            System.out.println("  shutdown() 返回 void，队列里的任务会继续执行完");
        }

        // 两者都是立刻返回，想等结束必须配合 awaitTermination
        boolean terminated = pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  10 秒内是否完全终止 = " + terminated);
        System.out.println("  最终完成任务数 = " + pool.getCompletedTaskCount() + " / 5");
    }
}
