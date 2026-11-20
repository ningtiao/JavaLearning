import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 第 16 节演示 3：一边写一边遍历会发生什么
 *
 * 这是 Collections.synchronizedXxx() 最大的陷阱：
 * 它只给每个方法包了 synchronized，遍历完全不在保护范围内。
 */
public class IterationSafetyDemo {

    public static void main(String[] args) throws InterruptedException {

        // 初始放 5 万个元素，让"遍历"这个动作本身持续足够长时间，
        // 否则主线程一瞬间就遍历完了，写线程还没来得及插进来，测不出问题。
        final int INIT_SIZE = 50_000;

        // ========== 1. synchronizedList：遍历时被修改 → 抛异常 ==========
        System.out.println("=== 1. synchronizedList 不加锁遍历（危险）===");
        int crashed = 0;
        for (int attempt = 1; attempt <= 3; attempt++) {
            List<Integer> list = Collections.synchronizedList(newList(INIT_SIZE));

            Thread writer = new Thread(() -> {
                for (int i = 0; i < 50_000; i++) list.add(i);
            });
            writer.start();

            try {
                long sum = 0;
                for (int v : list) sum += v;          // ❌ 没加锁的遍历
                System.out.println("  第" + attempt + "次：侥幸没抛异常，sum=" + sum);
            } catch (ConcurrentModificationException e) {
                crashed++;
                System.out.println("  第" + attempt + "次：抛出 "
                        + e.getClass().getSimpleName() + "  ← 遍历被写操作打断");
            }
            writer.join();
        }
        System.out.println("  3 次中有 " + crashed + " 次抛异常"
                + "（具体次数随运行波动，但这种写法随时可能炸）");

        // ========== 2. synchronizedList：正确的遍历方式 ==========
        System.out.println("\n=== 2. synchronizedList 手动锁住整个遍历（正确）===");
        for (int attempt = 1; attempt <= 3; attempt++) {
            List<Integer> list2 = Collections.synchronizedList(newList(INIT_SIZE));
            Thread writer2 = new Thread(() -> {
                for (int i = 0; i < 50_000; i++) list2.add(i);
            });
            writer2.start();

            long sum2 = 0;
            synchronized (list2) {                    // ✅ 必须锁住整个遍历过程
                for (int v : list2) sum2 += v;
            }
            writer2.join();
            System.out.println("  第" + attempt + "次：加锁遍历成功，遍历期间不会被插入");
        }

        // ========== 3. CopyOnWriteArrayList：迭代器是快照 ==========
        System.out.println("\n=== 3. CopyOnWriteArrayList 快照遍历（永不抛异常）===");
        for (int attempt = 1; attempt <= 3; attempt++) {
            List<Integer> cow = new CopyOnWriteArrayList<>(newList(INIT_SIZE));

            Thread writer3 = new Thread(() -> {
                for (int i = 0; i < 20_000; i++) cow.add(i);
            });
            writer3.start();

            int count = 0;
            for (int v : cow) {                       // 拿的是创建迭代器那一刻的快照
                count++;
            }
            writer3.join();
            System.out.println("  第" + attempt + "次：遍历到 " + count
                    + " 个元素（快照大小），最终 size=" + cow.size());
        }

        // ========== 4. CopyOnWriteArrayList 的写性能代价 ==========
        System.out.println("\n=== 4. 写性能对比（各 add 20000 次，单线程）===");
        int n = 20000;

        List<Integer> cow = new CopyOnWriteArrayList<>();
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) cow.add(i);
        long cowCost = System.currentTimeMillis() - t1;

        List<Integer> syncList = Collections.synchronizedList(new ArrayList<>());
        long t2 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) syncList.add(i);
        long syncCost = System.currentTimeMillis() - t2;

        System.out.println("  CopyOnWriteArrayList add " + n + " 次: " + cowCost + " ms");
        System.out.println("  synchronizedList     add " + n + " 次: " + syncCost + " ms");
        System.out.println("  COW 每次写都复制整个数组，是 O(n) 操作 —— 只适合读多写极少");
    }

    /** 造一个有 n 个元素的普通 ArrayList */
    private static List<Integer> newList(int n) {
        List<Integer> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(1);
        return l;
    }
}
