import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 演示：提交任务 → 定期监控 → 优雅关闭
 */
public class OrderThreadPoolDemo {

    /** 用 LongAdder 统计业务完成数（高并发计数首选） */
    private static final LongAdder finished = new LongAdder();

    public static void main(String[] args) throws InterruptedException {

        System.out.println("CPU 核数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("初始 " + OrderThreadPool.stats());
        System.out.println("------------------------------------------------------------");

        // 提交 50 个模拟订单任务
        for (int i = 1; i <= 50; i++) {
            final int orderId = i;
            OrderThreadPool.POOL.execute(() -> handleOrder(orderId));
        }

        System.out.println("提交 50 个任务后 " + OrderThreadPool.stats());

        // 模拟监控线程：每 100 ms 采样一次
        for (int i = 0; i < 3; i++) {
            Thread.sleep(100);
            System.out.println("监控采样 " + (i + 1) + "：" + OrderThreadPool.stats());
        }

        System.out.println("------------------------------------------------------------");
        System.out.println("开始优雅关闭……");
        OrderThreadPool.shutdownGracefully();

        System.out.println("关闭完成 " + OrderThreadPool.stats());
        System.out.println("业务计数器统计的完成数 = " + finished.sum());
        System.out.println("线程池是否已终止 = " + OrderThreadPool.POOL.isTerminated());
    }

    /** 模拟订单处理：耗时 50 ms，其中第 7 个订单会抛异常 */
    private static void handleOrder(int orderId) {
        // 最外层兜住所有异常，这是最推荐的做法
        try {
            if (orderId == 7) {
                throw new IllegalStateException("订单 7 数据异常（故意制造的）");
            }
            TimeUnit.MILLISECONDS.sleep(50);
            finished.increment();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.printf("[%s] 处理订单 %d 失败: %s%n",
                    Thread.currentThread().getName(), orderId, e.getMessage());
        }
    }
}
