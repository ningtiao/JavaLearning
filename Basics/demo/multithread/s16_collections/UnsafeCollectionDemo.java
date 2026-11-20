import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 第 16 节演示 1：线程不安全的集合在并发下会丢数据
 *
 * 10 个线程，每个往集合里放 1000 个元素，期望总数 10000。
 * 注意：HashMap 的 key 互不重复，所以丢数据不是"覆盖"，纯粹是并发 bug。
 */
public class UnsafeCollectionDemo {

    private static final int THREADS = 10;
    private static final int PER_THREAD = 1000;
    private static final int EXPECTED = THREADS * PER_THREAD;

    public static void main(String[] args) throws InterruptedException {

        System.out.println("10 个线程 × 每个 1000 个元素，期望总数 = " + EXPECTED);
        System.out.println("（每次运行结果都不同，但线程不安全的那些一定小于期望值）");

        // ============ Map 对比 ============
        System.out.println("\n=== Map ===");

        Map<Integer, Integer> hashMap = new HashMap<>();
        runAll(i -> {
            int base = i * PER_THREAD;
            for (int j = 0; j < PER_THREAD; j++) {
                hashMap.put(base + j, j);          // key 互不重复
            }
        });
        report("HashMap          ", hashMap.size());

        Map<Integer, Integer> chm = new ConcurrentHashMap<>();
        runAll(i -> {
            int base = i * PER_THREAD;
            for (int j = 0; j < PER_THREAD; j++) {
                chm.put(base + j, j);
            }
        });
        report("ConcurrentHashMap", chm.size());

        Map<Integer, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());
        runAll(i -> {
            int base = i * PER_THREAD;
            for (int j = 0; j < PER_THREAD; j++) {
                syncMap.put(base + j, j);
            }
        });
        report("synchronizedMap  ", syncMap.size());

        // ============ List 对比 ============
        System.out.println("\n=== List ===");

        List<Integer> arrayList = new ArrayList<>();
        runAllCatching("ArrayList            ", arrayList);

        List<Integer> cow = new CopyOnWriteArrayList<>();
        runAllCatching("CopyOnWriteArrayList ", cow);

        List<Integer> syncList = Collections.synchronizedList(new ArrayList<>());
        runAllCatching("synchronizedList     ", syncList);
    }

    /** 启动 THREADS 个线程并等它们全部结束 */
    private static void runAll(java.util.function.IntConsumer task) throws InterruptedException {
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            ts[i] = new Thread(() -> task.accept(id));
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();      // 必须 join，否则主线程读到的是中间状态
    }

    /** List 的并发 add 可能抛数组越界，所以要兜住异常 */
    private static void runAllCatching(String name, List<Integer> list)
            throws InterruptedException {
        final StringBuilder err = new StringBuilder();
        runAll(i -> {
            for (int j = 0; j < PER_THREAD; j++) {
                try {
                    list.add(j);
                } catch (Exception e) {
                    synchronized (err) {
                        if (err.length() == 0) {
                            err.append(e.getClass().getSimpleName());
                        }
                    }
                }
            }
        });
        String suffix = err.length() > 0 ? "   ⚠️ 还抛出了 " + err : "";
        report(name, list.size());
        if (!suffix.isEmpty()) System.out.println("     " + suffix);
    }

    private static void report(String name, int actual) {
        String mark = actual == EXPECTED ? "✅ 正确" : "❌ 丢了 " + (EXPECTED - actual) + " 个";
        System.out.printf("  %s 实际 = %-6d %s%n", name, actual, mark);
    }
}
