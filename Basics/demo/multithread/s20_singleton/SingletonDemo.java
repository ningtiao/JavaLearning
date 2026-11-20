import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 第 20 节演示：四种线程安全的单例写法
 *
 * 每种都用 20 个线程并发获取，验证：
 *   - 构造方法只执行了一次
 *   - 20 个线程拿到的是同一个实例
 * 最后演示反射攻击，看哪种写法防得住。
 */
public class SingletonDemo {

    public static void main(String[] args) throws Exception {

        System.out.println("=== 写法一：饿汉式（类加载时创建）===");
        verify(Hungry::getInstance);

        System.out.println("\n=== 写法二：双重检查锁 DCL（延迟加载）===");
        verify(Dcl::getInstance);

        System.out.println("\n=== 写法三：静态内部类（延迟加载，推荐）===");
        verify(Holder::getInstance);

        System.out.println("\n=== 写法四：枚举（最省事）===");
        verify(() -> EnumSingleton.INSTANCE);

        // ============ 反射攻击 ============
        System.out.println("\n=== 反射攻击：前三种都能被破解 ===");
        attackByReflection(Hungry.class, "饿汉式");
        attackByReflection(Dcl.class, "DCL");
        attackByReflection(Holder.class, "静态内部类");

        System.out.println("\n=== 反射攻击枚举：JVM 层面拦住 ===");
        try {
            Constructor<EnumSingleton> c =
                    EnumSingleton.class.getDeclaredConstructor(String.class, int.class);
            c.setAccessible(true);
            c.newInstance("HACKED", 1);
            System.out.println("  ❌ 枚举被破解了（不应该发生）");
        } catch (Exception e) {
            System.out.println("  ✅ 枚举防住了: " + e.getClass().getSimpleName());
            System.out.println("     " + e.getMessage());
        }

        // ============ 延迟加载验证 ============
        System.out.println("\n=== 延迟加载验证 ===");
        System.out.println("  Lazy 类还没被使用，构造方法应该还没执行");
        System.out.println("  （上面没有出现 'Lazy 的构造方法被执行'）");
        System.out.println("  现在第一次调用 getInstance()：");
        Lazy.getInstance();
        System.out.println("  ← 构造方法刚才才执行，这就是静态内部类的延迟加载");
    }

    /** 用 20 个线程并发获取实例，检查是不是同一个 */
    private static void verify(java.util.function.Supplier<Object> getter)
            throws InterruptedException {
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<Object> instances = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();                 // 20 个线程同时起跑
                    instances.add(getter.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        System.out.println("  20 个线程拿到的不同实例个数 = " + instances.size()
                + (instances.size() == 1 ? "   ✅ 单例成立" : "   ❌ 单例被破坏"));
    }

    /** 用反射绕过私有构造方法 */
    private static void attackByReflection(Class<?> clazz, String name) throws Exception {
        Constructor<?> c = clazz.getDeclaredConstructor();
        c.setAccessible(true);                     // 强行打开私有构造
        Object o1 = c.newInstance();
        Object o2 = c.newInstance();
        System.out.println("  " + name + "：反射创建了两个实例，o1 == o2 ? " + (o1 == o2)
                + "   ❌ 单例被破解");
    }

    // ==================== 写法一：饿汉式 ====================
    static class Hungry {
        private static final Hungry INSTANCE = new Hungry();   // 类加载时就创建

        private Hungry() { }

        public static Hungry getInstance() {
            return INSTANCE;
        }
    }

    // ==================== 写法二：双重检查锁 DCL ====================
    static class Dcl {
        /** volatile 不能省！否则指令重排会让别的线程拿到未初始化完的对象 */
        private static volatile Dcl instance;

        private static final AtomicInteger constructCount = new AtomicInteger();

        private Dcl() {
            int n = constructCount.incrementAndGet();
            if (n == 1) {
                System.out.println("  Dcl 构造方法被执行（只应出现一次）");
            } else {
                System.out.println("  ⚠️ Dcl 构造方法被执行了第 " + n + " 次！");
            }
        }

        public static Dcl getInstance() {
            if (instance == null) {                    // 第一次检查：无锁，快
                synchronized (Dcl.class) {
                    if (instance == null) {            // 第二次检查：有锁，准
                        instance = new Dcl();
                    }
                }
            }
            return instance;
        }
    }

    // ==================== 写法三：静态内部类 ====================
    static class Holder {
        private Holder() { }

        /** 内部类在首次被使用时才加载，由 JVM 保证线程安全 */
        private static class InstanceHolder {
            private static final Holder INSTANCE = new Holder();
        }

        public static Holder getInstance() {
            return InstanceHolder.INSTANCE;
        }
    }

    // ==================== 写法四：枚举 ====================
    enum EnumSingleton {
        INSTANCE;

        private final long createdAt = System.nanoTime();

        public long getCreatedAt() {
            return createdAt;
        }
    }

    // ==================== 用于演示延迟加载 ====================
    static class Lazy {
        private Lazy() {
            System.out.println("  >>> Lazy 的构造方法被执行");
        }

        private static class H {
            private static final Lazy INSTANCE = new Lazy();
        }

        public static Lazy getInstance() {
            return H.INSTANCE;
        }
    }
}
