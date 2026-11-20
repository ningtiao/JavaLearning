import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 第 17 节演示 2：线程池里不 remove() 会发生什么（最重要的一个演示）
 *
 * 比内存泄漏更危险的是【脏数据】：
 * 线程池复用线程，上一个请求残留的 ThreadLocal 值会被下一个请求读到 ——
 * 用户 A 的上下文串给了用户 B，这是严重的安全问题。
 *
 * 为了让现象 100% 稳定复现，这里用【单线程】线程池，
 * 保证两次任务一定跑在同一个线程上。
 */
public class ThreadLocalPollutionDemo {

    /** 模拟存放当前登录用户的上下文 */
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {

        // 单线程池：保证两个请求跑在同一个线程上
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));

        // ============ 错误示范：不 remove ============
        System.out.println("=== ❌ 错误示范：用完不 remove() ===");

        pool.submit(() -> {
            CURRENT_USER.set("张三");                    // 请求1：张三登录
            System.out.println("  [请求1] 线程=" + Thread.currentThread().getName()
                    + "，设置当前用户=" + CURRENT_USER.get());
            // 忘记 remove！
        }).get();

        pool.submit(() -> {
            // 请求2：这是一个未登录的请求，它根本没有 set
            String user = CURRENT_USER.get();
            System.out.println("  [请求2] 线程=" + Thread.currentThread().getName()
                    + "，没有 set 却读到当前用户=" + user);
            if (user != null) {
                System.out.println("  ⚠️ 严重问题：请求2 读到了请求1 残留的身份！");
                System.out.println("  ⚠️ 真实系统里这意味着：用户B 看到了用户A 的数据");
            }
        }).get();

        // ============ 正确示范：finally 里 remove ============
        System.out.println("\n=== ✅ 正确示范：finally 里 remove() ===");

        pool.submit(() -> {
            try {
                CURRENT_USER.set("李四");
                System.out.println("  [请求3] 设置当前用户=" + CURRENT_USER.get());
                throw new RuntimeException("业务异常");    // 即使抛异常
            } catch (Exception e) {
                System.out.println("  [请求3] 捕获异常: " + e.getMessage());
            } finally {
                CURRENT_USER.remove();                     // finally 保证一定清理
                System.out.println("  [请求3] 已 remove()");
            }
        }).get();

        pool.submit(() -> {
            System.out.println("  [请求4] 线程=" + Thread.currentThread().getName()
                    + "，读到当前用户=" + CURRENT_USER.get() + "   ← 干净的 null，正确");
        }).get();

        // ============ 内存泄漏演示 ============
        System.out.println("\n=== 内存泄漏：value 一直挂在线程上 ===");
        ThreadLocal<byte[]> bigData = new ThreadLocal<>();

        pool.submit(() -> {
            bigData.set(new byte[20 * 1024 * 1024]);       // 20MB
            System.out.println("  任务里放了一个 20MB 的对象，但没有 remove");
        }).get();

        System.gc();
        Thread.sleep(200);
        pool.submit(() -> {
            byte[] leaked = bigData.get();
            System.out.println("  下一个任务里还能读到它: "
                    + (leaked == null ? "null（已释放）"
                                      : leaked.length / 1024 / 1024 + "MB（仍然占着内存！）"));
            System.out.println("  线程池的线程不会销毁 → 这 20MB 永远回收不了");
            bigData.remove();                              // 补救
        }).get();

        pool.submit(() -> System.out.println("  remove 之后再读: " + bigData.get())).get();

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n=== 结论 ===");
        System.out.println("  铁律：用了 ThreadLocal.set()，就一定要在 finally 里 remove()");
    }
}
