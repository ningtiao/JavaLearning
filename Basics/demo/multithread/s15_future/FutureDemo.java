import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 第 15 节演示 1：Future 的基本用法，以及 get() 位置决定是否并行
 */
public class FutureDemo {

    public static void main(String[] args) throws Exception {

        // 演示用线程池，参数手动指定（不用 Executors）
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                5, 5, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));

        // ========== 1. Future 的基本 API ==========
        System.out.println("=== 1. Future 基本 API ===");
        Future<Integer> f = pool.submit(() -> {
            Thread.sleep(300);
            return 42;
        });
        System.out.println("  提交后立刻 isDone() = " + f.isDone());
        System.out.println("  get() 拿到结果       = " + f.get());   // 阻塞 300ms
        System.out.println("  完成后 isDone()      = " + f.isDone());

        // 带超时的 get
        System.out.println("\n=== 2. get(timeout) 超时 ===");
        Future<Integer> slow = pool.submit(() -> {
            Thread.sleep(2000);
            return 1;
        });
        try {
            slow.get(200, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("  200ms 内没拿到结果 -> " + e.getClass().getSimpleName());
        }
        System.out.println("  cancel(true) 返回 = " + slow.cancel(true));
        System.out.println("  isCancelled()     = " + slow.isCancelled());

        // ========== 3. 关键对比：get() 写在哪里，决定并行还是串行 ==========
        System.out.println("\n=== 3. get() 位置决定并行还是串行 ===");

        List<Callable<Integer>> tasks = Arrays.asList(
                newTask(1), newTask(2), newTask(3));

        // ❌ 错误姿势：提交一个就 get 一个
        long t1 = System.currentTimeMillis();
        int sum1 = 0;
        for (Callable<Integer> c : tasks) {
            sum1 += pool.submit(c).get();     // 提交后立刻阻塞，下一个根本没提交出去
        }
        long cost1 = System.currentTimeMillis() - t1;
        System.out.printf("  ❌ 边提交边 get : sum=%d, 耗时 %d ms（串行了）%n", sum1, cost1);

        // ✅ 正确姿势：先全部提交，再统一 get
        long t2 = System.currentTimeMillis();
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> c : tasks) {
            futures.add(pool.submit(c));      // 先全部扔进线程池
        }
        int sum2 = 0;
        for (Future<Integer> fu : futures) {
            sum2 += fu.get();                 // 再统一收结果
        }
        long cost2 = System.currentTimeMillis() - t2;
        System.out.printf("  ✅ 先全提交再 get: sum=%d, 耗时 %d ms（并行）%n", sum2, cost2);

        System.out.printf("  结果一样，耗时相差约 %.1f 倍%n", cost1 * 1.0 / cost2);

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** 造一个耗时 300ms、返回 1 的任务 */
    private static Callable<Integer> newTask(int id) {
        return () -> {
            Thread.sleep(300);
            return 1;
        };
    }
}
