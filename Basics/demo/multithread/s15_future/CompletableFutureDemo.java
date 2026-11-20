import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 第 15 节演示 2：CompletableFuture 的常用 API
 */
public class CompletableFutureDemo {

    private static ThreadPoolExecutor pool;

    public static void main(String[] args) throws Exception {

        pool = new ThreadPoolExecutor(
                8, 8, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50));

        // ========== 1. thenCombine：两个任务并行，合并结果 ==========
        System.out.println("=== 1. thenCombine 并行合并 ===");
        long start = System.currentTimeMillis();

        CompletableFuture<String> userInfo = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            return "张三";
        }, pool);                                     // 永远显式传线程池！

        CompletableFuture<Integer> orderCount = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            return 7;
        }, pool);

        String combined = userInfo
                .thenCombine(orderCount, (u, c) -> u + " 有 " + c + " 个订单")
                .get();

        System.out.println("  " + combined);
        System.out.println("  两个 300ms 任务并行，总耗时 "
                + (System.currentTimeMillis() - start) + " ms");

        // ========== 2. thenApply / thenAccept：串行转换 ==========
        System.out.println("\n=== 2. thenApply 串行转换 ===");
        Integer len = CompletableFuture
                .supplyAsync(() -> "hello world", pool)
                .thenApply(String::toUpperCase)        // 转大写
                .thenApply(String::length)             // 再取长度
                .get();
        System.out.println("  \"hello world\" -> 大写 -> 长度 = " + len);

        // ========== 3. thenCompose：结果再触发一个异步任务（扁平化） ==========
        System.out.println("\n=== 3. thenCompose 扁平化嵌套 ===");
        String composed = CompletableFuture
                .supplyAsync(() -> 100L, pool)                       // 先拿到 userId
                .thenCompose(id -> CompletableFuture.supplyAsync(     // 再用它查名字
                        () -> "用户" + id, pool))
                .get();
        System.out.println("  先查 id 再查名字 = " + composed);

        // ========== 4. exceptionally：异常降级 ==========
        System.out.println("\n=== 4. exceptionally 异常降级 ===");
        String fallback = CompletableFuture
                .supplyAsync(() -> {
                    throw new RuntimeException("远程调用失败");
                }, pool)
                .exceptionally(e -> "降级默认值 (" + e.getCause().getMessage() + ")")
                .thenApply(Object::toString)
                .get();
        System.out.println("  " + fallback);
        System.out.println("  注意：异常被包成 CompletionException，真异常在 getCause() 里");

        // ========== 5. handle：正常和异常统一处理 ==========
        System.out.println("\n=== 5. handle 统一处理 ===");
        String h1 = CompletableFuture.supplyAsync(() -> "正常结果", pool)
                .handle((r, e) -> e == null ? "成功: " + r : "失败: " + e.getMessage())
                .get();
        String h2 = CompletableFuture.supplyAsync(() -> {
                    throw new IllegalStateException("炸了");
                }, pool)
                .handle((r, e) -> e == null ? "成功: " + r : "失败: " + e.getCause().getMessage())
                .get();
        System.out.println("  " + h1);
        System.out.println("  " + h2);

        // ========== 6. allOf：等全部完成 ==========
        System.out.println("\n=== 6. allOf 等全部完成 ===");
        long t = System.currentTimeMillis();
        CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> { sleep(200); return "A"; }, pool);
        CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> { sleep(300); return "B"; }, pool);
        CompletableFuture<String> c = CompletableFuture.supplyAsync(() -> { sleep(100); return "C"; }, pool);

        CompletableFuture.allOf(a, b, c).get();        // 返回 Void，要自己取各自结果
        System.out.println("  结果 = " + a.get() + b.get() + c.get()
                + "，耗时 " + (System.currentTimeMillis() - t) + " ms（≈最慢的 300ms）");

        // ========== 7. applyToEither：用先完成的那个（超时降级常用） ==========
        System.out.println("\n=== 7. applyToEither 取先完成的 ===");
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> { sleep(100); return "快的赢了"; }, pool);
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> { sleep(500); return "慢的"; }, pool);
        System.out.println("  " + fast.applyToEither(slow, r -> r).get());

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
