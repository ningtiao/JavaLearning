import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 第 18 节演示 1：CountDownLatch —— 一个线程等 N 个任务完成
 *
 * 包含三部分：
 *   1. 基本用法：主线程等 3 个子任务
 *   2. 唯一的致命坑：任务抛异常没 countDown → await() 永久阻塞
 *   3. 正确写法：countDown 放 finally + await 带超时
 */
public class CountDownLatchDemo {

    public static void main(String[] args) throws InterruptedException {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                5, 5, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));

        // ========== 1. 基本用法 ==========
        System.out.println("=== 1. 基本用法：主线程等 3 个子任务 ===");
        int n = 3;
        CountDownLatch latch = new CountDownLatch(n);
        long start = System.currentTimeMillis();

        for (int i = 1; i <= n; i++) {
            final int id = i;
            pool.execute(() -> {
                sleep(id * 200L);                     // 分别耗时 200/400/600 ms
                System.out.println("  子任务" + id + " 完成");
                latch.countDown();                    // 完成一个，计数 -1
            });
        }

        System.out.println("  主线程等待中…… 当前计数=" + latch.getCount());
        latch.await();                                // 阻塞到计数为 0
        System.out.println("  全部完成，总耗时 " + (System.currentTimeMillis() - start)
                + " ms（串行需要 1200 ms）");

        // ========== 2. 致命坑：任务异常导致 await 永久阻塞 ==========
        System.out.println("\n=== 2. ❌ 任务抛异常却没 countDown ===");
        CountDownLatch badLatch = new CountDownLatch(2);

        pool.execute(() -> {
            System.out.println("  任务A 正常完成");
            badLatch.countDown();
        });
        pool.execute(() -> {
            System.out.println("  任务B 抛异常了（countDown 写在异常之后，执行不到）");
            if (true) throw new RuntimeException("业务异常");
            badLatch.countDown();                     // 永远执行不到
        });

        System.out.println("  用 await(1秒) 试探：");
        boolean ok = badLatch.await(1, TimeUnit.SECONDS);
        System.out.println("  await 返回 " + ok + "，剩余计数=" + badLatch.getCount());
        System.out.println("  ⚠️ 如果用无参 await()，主线程会在这里永久卡死！");

        // ========== 3. 正确写法 ==========
        System.out.println("\n=== 3. ✅ 正确写法：countDown 放 finally + await 带超时 ===");
        CountDownLatch goodLatch = new CountDownLatch(3);

        for (int i = 1; i <= 3; i++) {
            final int id = i;
            pool.execute(() -> {
                try {
                    if (id == 2) throw new RuntimeException("任务" + id + " 失败");
                    sleep(100);
                    System.out.println("  任务" + id + " 成功");
                } catch (Exception e) {
                    System.out.println("  任务" + id + " 异常: " + e.getMessage());
                } finally {
                    goodLatch.countDown();            // 不管成功失败都要减
                }
            });
        }

        if (!goodLatch.await(5, TimeUnit.SECONDS)) {
            System.out.println("  等待超时，部分任务未完成");
        } else {
            System.out.println("  全部任务已结束（含失败的），计数归零");
        }

        // ========== 4. CountDownLatch 不能复用 ==========
        System.out.println("\n=== 4. CountDownLatch 是一次性的 ===");
        CountDownLatch once = new CountDownLatch(1);
        once.countDown();
        System.out.println("  归零后计数 = " + once.getCount());
        once.countDown();                             // 再减也没用
        System.out.println("  再 countDown() 一次，计数还是 = " + once.getCount());
        once.await();                                 // 立刻返回，不阻塞
        System.out.println("  await() 立刻返回，不再阻塞 —— 想循环使用请用 CyclicBarrier");

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
