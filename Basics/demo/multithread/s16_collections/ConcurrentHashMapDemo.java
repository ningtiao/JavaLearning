import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 第 16 节演示 2：ConcurrentHashMap 的正确用法
 *
 * 重点：单个方法原子 ≠ 组合操作原子
 */
public class ConcurrentHashMapDemo {

    public static void main(String[] args) throws InterruptedException {

        // ========== 1. 不允许 null 键和 null 值 ==========
        System.out.println("=== 1. null 的处理 ===");
        Map<String, String> hashMap = new java.util.HashMap<>();
        hashMap.put(null, null);
        System.out.println("  HashMap 可以存 null：" + hashMap);

        try {
            new ConcurrentHashMap<String, String>().put("k", null);
        } catch (NullPointerException e) {
            System.out.println("  ConcurrentHashMap 存 null 值 -> NullPointerException");
        }
        try {
            new ConcurrentHashMap<String, String>().put(null, "v");
        } catch (NullPointerException e) {
            System.out.println("  ConcurrentHashMap 存 null 键 -> NullPointerException");
        }

        // ========== 2. 组合操作不原子：containsKey + put ==========
        System.out.println("\n=== 2. containsKey + put 不是原子的 ===");
        demoNonAtomic();

        // ========== 3. computeIfAbsent 保证只计算一次 ==========
        System.out.println("\n=== 3. computeIfAbsent 保证初始化只执行一次 ===");
        demoComputeIfAbsent();

        // ========== 4. 计数器的两种正确写法 ==========
        System.out.println("\n=== 4. 计数器写法 ===");
        demoCounter();
    }

    /** 20 个线程同时对同一个 key 做 "containsKey 则不放" —— 慢加载函数会被执行多次 */
    private static void demoNonAtomic() throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try {
                    start.await();                       // 让 20 个线程同时起跑
                    // ❌ 两步操作，中间可能被插入
                    if (!cache.containsKey("config")) {
                        cache.put("config", loadFromDb(loadCount));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        System.out.println("  ❌ containsKey+put：慢加载函数被执行了 "
                + loadCount.get() + " 次（期望 1 次）");
    }

    /** 同样 20 个线程，改用 computeIfAbsent */
    private static void demoComputeIfAbsent() throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    // ✅ 原子的复合方法
                    cache.computeIfAbsent("config", k -> loadFromDb(loadCount));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        System.out.println("  ✅ computeIfAbsent：慢加载函数被执行了 "
                + loadCount.get() + " 次");
        System.out.println("     这就是本地缓存的标准写法");
    }

    /** 并发计数：merge 和 computeIfAbsent+AtomicInteger 两种写法 */
    private static void demoCounter() throws InterruptedException {
        // 写法一：Map<String,Integer> + merge
        ConcurrentHashMap<String, Integer> c1 = new ConcurrentHashMap<>();
        // 写法二：Map<String,AtomicInteger> + computeIfAbsent
        ConcurrentHashMap<String, AtomicInteger> c2 = new ConcurrentHashMap<>();

        Thread[] ts = new Thread[10];
        for (int i = 0; i < 10; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    c1.merge("hit", 1, Integer::sum);
                    c2.computeIfAbsent("hit", k -> new AtomicInteger()).incrementAndGet();
                }
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        System.out.println("  期望 10000");
        System.out.println("  merge(key, 1, Integer::sum)                        = " + c1.get("hit"));
        System.out.println("  computeIfAbsent(...).incrementAndGet()             = " + c2.get("hit"));
    }

    /** 模拟一个耗时 50ms 的数据库加载 */
    private static String loadFromDb(AtomicInteger counter) {
        counter.incrementAndGet();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "配置内容";
    }
}
