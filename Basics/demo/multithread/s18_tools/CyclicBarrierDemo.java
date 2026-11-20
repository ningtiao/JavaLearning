import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 第 18 节演示 2：CyclicBarrier —— N 个线程互相等，到齐了一起走
 *
 * 和 CountDownLatch 的区别：
 *   CountDownLatch：一个线程等 N 个任务，一次性
 *   CyclicBarrier ：N 个线程互相等，可循环使用
 */
public class CyclicBarrierDemo {

    public static void main(String[] args) throws InterruptedException {

        int parties = 3;        // 3 个线程
        int rounds = 2;         // 跑 2 轮，验证可复用

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                parties, parties, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));

        // 第二个参数：所有线程到齐后，由【最后到达的那个线程】执行这个回调
        CyclicBarrier barrier = new CyclicBarrier(parties, () ->
                System.out.println("  ---- 本轮全部到齐，由 "
                        + Thread.currentThread().getName() + " 执行屏障回调 ----"));

        System.out.println("=== 3 个线程、2 轮，每轮到齐才能进入下一轮 ===");
        System.out.println("  （每个线程每轮耗时不同，快的必须等慢的）\n");

        for (int i = 1; i <= parties; i++) {
            final int workerId = i;
            pool.execute(() -> {
                try {
                    for (int round = 1; round <= rounds; round++) {
                        // 每个线程耗时不同：workerId * 200ms
                        long cost = workerId * 200L;
                        Thread.sleep(cost);
                        System.out.printf("  第%d轮：工人%d 完成自己的活（耗时 %d ms），到达屏障%n",
                                round, workerId, cost);

                        int arriveIndex = barrier.await();   // 等其他线程都到这里
                        // await 的返回值：到达顺序的索引，最后到的是 0
                        if (arriveIndex == 0) {
                            System.out.println("      （工人" + workerId + " 是最后到达的）");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    System.out.println("  屏障被破坏: " + e.getMessage());
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n=== barrier 可以重复使用 ===");
        System.out.println("  parties = " + barrier.getParties()
                + "，当前等待数 = " + barrier.getNumberWaiting()
                + "，是否已破坏 = " + barrier.isBroken());

        // ========== 对比 CountDownLatch ==========
        System.out.println("\n=== 和 CountDownLatch 的区别 ===");
        System.out.println("  CountDownLatch：一个线程等 N 个任务，归零后失效（一次性）");
        System.out.println("  CyclicBarrier ：N 个线程互相等，到齐后自动重置（可循环）");
        System.out.println("  典型场景：");
        System.out.println("    CountDownLatch -> 主线程等所有子任务完成（聚合接口结果）");
        System.out.println("    CyclicBarrier  -> 多阶段并行计算、并发压测的统一起跑线");
    }
}
