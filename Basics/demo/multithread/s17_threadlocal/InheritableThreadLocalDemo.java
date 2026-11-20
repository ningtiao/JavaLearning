import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 第 17 节演示 3：子线程能不能拿到父线程的 ThreadLocal 值
 *
 * 三个结论：
 *   1. 普通 ThreadLocal：子线程拿不到 → null
 *   2. InheritableThreadLocal：新建子线程时会复制 → 能拿到
 *   3. InheritableThreadLocal + 线程池：失效！因为线程是复用的，不是新建的
 */
public class InheritableThreadLocalDemo {

    private static final ThreadLocal<String> PLAIN = new ThreadLocal<>();
    private static final InheritableThreadLocal<String> INHERITABLE = new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {

        PLAIN.set("父线程的值");
        INHERITABLE.set("父线程的值");

        // ========== 1. 普通 ThreadLocal：子线程读不到 ==========
        System.out.println("=== 1. 普通 ThreadLocal ===");
        Thread t1 = new Thread(() ->
                System.out.println("  子线程读 PLAIN = " + PLAIN.get() + "   ← 拿不到"));
        t1.start();
        t1.join();

        // ========== 2. InheritableThreadLocal：新建线程能读到 ==========
        System.out.println("\n=== 2. InheritableThreadLocal（新建线程）===");
        Thread t2 = new Thread(() ->
                System.out.println("  子线程读 INHERITABLE = " + INHERITABLE.get() + "   ← 拿到了"));
        t2.start();
        t2.join();

        // ========== 3. 线程池下 InheritableThreadLocal 失效 ==========
        System.out.println("\n=== 3. InheritableThreadLocal + 线程池（会出错）===");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));

        // 先触发一次，把池里的线程创建出来（此时复制的是"请求A"的值）
        INHERITABLE.set("请求A的上下文");
        pool.submit(() -> System.out.println("  第1次提交，线程池内读到 = "
                + INHERITABLE.get() + "   ← 正确，因为线程此刻才被创建")).get();

        // 换成请求B 的上下文，再提交
        INHERITABLE.set("请求B的上下文");
        pool.submit(() -> System.out.println("  第2次提交，线程池内读到 = "
                + INHERITABLE.get() + "   ← ⚠️ 错了！还是请求A 的")).get();

        INHERITABLE.set("请求C的上下文");
        pool.submit(() -> System.out.println("  第3次提交，线程池内读到 = "
                + INHERITABLE.get() + "   ← ⚠️ 依然是请求A 的")).get();

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n=== 原因 ===");
        System.out.println("  InheritableThreadLocal 只在【线程创建那一刻】复制父线程的值。");
        System.out.println("  线程池里的线程早就创建好了，之后一直复用 ——");
        System.out.println("  所以它永远停留在第一次创建时捕获的那个值上。");
        System.out.println("\n=== 解决方案 ===");
        System.out.println("  线程池场景用阿里的 TransmittableThreadLocal（TTL）：");
        System.out.println("  它在【任务提交时】捕获上下文、在【任务执行时】还原。");
        System.out.println("  JDK 21 的 ScopedValue（预览特性）也在解决同一类问题。");
    }
}
