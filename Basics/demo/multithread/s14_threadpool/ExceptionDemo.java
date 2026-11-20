import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示：线程池里的异常处理，四种情况对比
 *   1. submit() + 不 get()  → 异常被完全吞掉，什么都看不到
 *   2. submit() + get()     → 能拿到异常，但包了一层 ExecutionException
 *   3. execute()            → 异常走 UncaughtExceptionHandler
 *   4. 任务内部 try-catch   → 最推荐
 */
public class ExceptionDemo {

    public static void main(String[] args) throws Exception {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                namedFactory("demo"));

        // ---------- 情况 1：submit 不 get，异常静默消失 ----------
        System.out.println("=== 情况1：submit() 且不调用 get() ===");
        pool.submit(() -> {
            throw new RuntimeException("情况1 的异常");
        });
        Thread.sleep(200);
        System.out.println("  上面什么都没打印 —— 异常被装进 Future 吞掉了\n");

        // ---------- 情况 2：submit + get，能拿到异常 ----------
        System.out.println("=== 情况2：submit() 且调用 get() ===");
        Future<?> future = pool.submit(() -> {
            throw new RuntimeException("情况2 的异常");
        });
        try {
            future.get();
        } catch (ExecutionException e) {
            System.out.println("  捕获到 " + e.getClass().getSimpleName());
            System.out.println("  真正的业务异常在 getCause() 里: " + e.getCause());
        }
        System.out.println();

        // ---------- 情况 3：execute，走 UncaughtExceptionHandler ----------
        System.out.println("=== 情况3：execute() ===");
        pool.execute(() -> {
            throw new RuntimeException("情况3 的异常");
        });
        Thread.sleep(200);
        System.out.println("  异常被 UncaughtExceptionHandler 打印出来了\n");

        // ---------- 情况 4：任务内部兜住，最推荐 ----------
        System.out.println("=== 情况4：任务内部 try-catch(Throwable) ===");
        pool.submit(() -> {
            try {
                throw new RuntimeException("情况4 的异常");
            } catch (Throwable t) {
                System.out.println("  任务内部自己处理了: " + t.getMessage());
                System.out.println("  这样既不丢信息，也不依赖调用方记得 get()");
            }
        });
        Thread.sleep(200);

        // ---------- 额外：抛异常的线程会被销毁并补新的 ----------
        System.out.println("\n=== 额外：抛异常的线程会被替换 ===");
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            pool.execute(() -> {
                System.out.println("  第" + id + "次任务，线程 = "
                        + Thread.currentThread().getName());
                throw new RuntimeException("故意抛出");
            });
            Thread.sleep(150);
        }
        System.out.println("  线程编号在变化 —— 抛异常的线程被销毁，线程池补了新线程进来");

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + seq.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, e) ->
                    System.err.printf("  [UncaughtExceptionHandler] %s 抛出: %s%n",
                            thread.getName(), e.getMessage()));
            return t;
        };
    }
}
