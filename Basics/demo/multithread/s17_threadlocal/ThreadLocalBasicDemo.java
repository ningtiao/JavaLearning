/**
 * 第 17 节演示 1：ThreadLocal 的基本隔离效果
 *
 * 两个线程用的是【同一个】ThreadLocal 对象，但各自读到自己写的值。
 */
public class ThreadLocalBasicDemo {

    // 注意：这是一个静态的、被所有线程共享的 ThreadLocal 实例
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    // withInitial 提供默认值，避免第一次 get() 返回 null
    private static final ThreadLocal<Integer> COUNTER = ThreadLocal.withInitial(() -> 0);

    public static void main(String[] args) throws InterruptedException {

        Runnable task = () -> {
            String name = Thread.currentThread().getName();

            USER.set("user-of-" + name);              // 各自写自己的值
            COUNTER.set(COUNTER.get() + 100);

            sleep(50);                                 // 故意等一下，让两个线程交错

            System.out.printf("  %s 读到 USER=%s, COUNTER=%d%n",
                    name, USER.get(), COUNTER.get());

            USER.remove();                             // 用完一定要清理
            COUNTER.remove();
        };

        System.out.println("=== 两个线程操作同一个 ThreadLocal 对象 ===");
        Thread t1 = new Thread(task, "T-1");
        Thread t2 = new Thread(task, "T-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("\n=== main 线程没 set 过，读到的是 ===");
        System.out.println("  USER    = " + USER.get() + "   （null，因为 main 没写过）");
        System.out.println("  COUNTER = " + COUNTER.get() + "      （0，来自 withInitial 的默认值）");

        System.out.println("\n=== 原理 ===");
        System.out.println("  数据不存在 ThreadLocal 对象里，而是存在每个 Thread 对象自己的");
        System.out.println("  ThreadLocalMap 中，ThreadLocal 实例只是查找用的 key。");
        System.out.println("  threadLocal.get() 等价于：");
        System.out.println("      Thread.currentThread().threadLocals.get(threadLocal)");

        System.out.println("\n=== 和加锁的区别 ===");
        System.out.println("  加锁       ：多个线程排队访问同一份数据   → 用时间换安全");
        System.out.println("  ThreadLocal：每个线程一份数据，根本不共享 → 用空间换安全");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
