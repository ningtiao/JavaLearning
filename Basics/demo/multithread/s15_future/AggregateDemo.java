import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 第 15 节演示 3：实战场景 —— 聚合多个远程接口
 *
 * 四个实践要点：
 *   1. 每个 supplyAsync 都显式传自定义线程池（不用默认的 commonPool）
 *   2. 每个任务单独 exceptionally 降级（一个接口挂了不影响其他）
 *   3. allOf(...).get(超时) 整体兜底
 *   4. 用 getNow(默认值) 取结果，避免超时后再次阻塞
 *
 * 跑两遍：第一遍全部正常，第二遍故意让订单接口超慢 + 优惠券接口抛异常。
 */
public class AggregateDemo {

    // ---------- 模拟的数据模型 ----------
    record UserInfo(long id, String name) {
        static UserInfo empty() { return new UserInfo(0, "未知用户"); }
    }
    record Order(String orderNo) { }
    record Coupon(String code) { }
    record DetailVO(UserInfo user, List<Order> orders, List<Coupon> coupons) {
        @Override public String toString() {
            return "用户=" + user.name() + ", 订单数=" + orders.size()
                    + ", 优惠券数=" + coupons.size();
        }
    }

    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            8, 16, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== 场景1：三个接口都正常（各 300ms）===");
        long t1 = System.currentTimeMillis();
        DetailVO vo1 = getDetail(1L, false);
        System.out.println("  结果: " + vo1);
        System.out.println("  耗时: " + (System.currentTimeMillis() - t1) + " ms（串行需要 900ms）");

        System.out.println("\n=== 场景2：订单接口超慢(2s) + 优惠券接口抛异常 ===");
        long t2 = System.currentTimeMillis();
        DetailVO vo2 = getDetail(2L, true);
        System.out.println("  结果: " + vo2);
        System.out.println("  耗时: " + (System.currentTimeMillis() - t2) + " ms（被 1s 超时兜住了）");
        System.out.println("  注意：用户信息正常返回，订单超时取默认值，优惠券降级为空");

        POOL.shutdown();
        POOL.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** 聚合三个接口 */
    private static DetailVO getDetail(long userId, boolean makeItFail) {

        CompletableFuture<UserInfo> fUser = CompletableFuture
                .supplyAsync(() -> userApi(userId), POOL)      // ← 显式指定线程池
                .exceptionally(e -> {                          // ← 单个失败不影响整体
                    System.out.println("  [降级] 用户接口失败: " + e.getMessage());
                    return UserInfo.empty();
                });

        CompletableFuture<List<Order>> fOrders = CompletableFuture
                .supplyAsync(() -> orderApi(userId, makeItFail), POOL)
                .exceptionally(e -> {
                    System.out.println("  [降级] 订单接口失败: " + e.getMessage());
                    return List.of();
                });

        CompletableFuture<List<Coupon>> fCoupons = CompletableFuture
                .supplyAsync(() -> couponApi(userId, makeItFail), POOL)
                .exceptionally(e -> {
                    System.out.println("  [降级] 优惠券接口失败: " + e.getCause().getMessage());
                    return List.of();
                });

        // 整体最多等 1 秒
        try {
            CompletableFuture.allOf(fUser, fOrders, fCoupons).get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("  [超时] 聚合整体超时，返回已拿到的部分数据");
        } catch (Exception e) {
            System.out.println("  [异常] 聚合失败: " + e.getMessage());
        }

        // 用 getNow 而不是 get：超时后未完成的任务直接拿默认值，不再阻塞
        return new DetailVO(
                fUser.getNow(UserInfo.empty()),
                fOrders.getNow(List.of()),
                fCoupons.getNow(List.of()));
    }

    // ---------- 模拟三个远程接口 ----------
    private static UserInfo userApi(long id) {
        sleep(300);
        return new UserInfo(id, "张三");
    }

    private static List<Order> orderApi(long id, boolean slow) {
        sleep(slow ? 2000 : 300);           // 场景2 故意超慢
        return List.of(new Order("ORD-1"), new Order("ORD-2"));
    }

    private static List<Coupon> couponApi(long id, boolean fail) {
        sleep(300);
        if (fail) {
            throw new IllegalStateException("优惠券服务不可用");
        }
        return List.of(new Coupon("C-100"));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
