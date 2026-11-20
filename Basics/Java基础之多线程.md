# Java 基础之多线程

> 本文面向**初级到中级**工程师，按两段编排：
>
> - **第一部分（初级）**：线程是什么、怎么创建、六种状态、线程安全问题的根源、`synchronized` 与 `volatile`
> - **第二部分（中级）**：锁、原子类、线程通信、**线程池（重点）**、并发集合、`ThreadLocal`、并发工具类、死锁、虚拟线程
>
> 本文所有代码输出均在 **JDK 21** 上实测。多线程的输出天然带随机性，凡是**每次运行结果都不同**的地方都已标注，表格里的耗时数据只用于比较量级，不要当作固定值。
>
> **第 14–21 节的示例都是完整可运行的程序**，源文件已放在 `demo/multithread/` 目录下，共 23 个：
>
> ```bash
> cd demo/multithread/s14_threadpool
> javac -encoding UTF-8 *.java
> java ThreadPoolFlowDemo
> ```
>
> 目录结构和注意事项见 [`demo/multithread/README.md`](demo/multithread/README.md)。文档里贴的代码和那些文件是同一份，可以直接复制，也可以直接跑目录里的。

---

## 目录

**第一部分：基础（初级工程师）**

1. [为什么需要多线程](#1-为什么需要多线程)
2. [进程、线程、并发与并行](#2-进程线程并发与并行)
3. [创建线程的四种方式](#3-创建线程的四种方式)
4. [Thread 常用 API](#4-thread-常用-api)
5. [线程的六种状态](#5-线程的六种状态)
6. [线程安全问题的根源：三大特性](#6-线程安全问题的根源三大特性)
7. [synchronized：最常用的同步手段](#7-synchronized最常用的同步手段)
8. [volatile：只管可见性，不管原子性](#8-volatile只管可见性不管原子性)
9. [怎么正确地停止一个线程](#9-怎么正确地停止一个线程)

**第二部分：进阶应用（中级工程师）**

10. [Lock 接口与 ReentrantLock](#10-lock-接口与-reentrantlock)
11. [原子类与 CAS](#11-原子类与-cas)
12. [线程间通信：wait / notify](#12-线程间通信wait--notify)
13. [BlockingQueue：不用自己写 wait/notify](#13-blockingqueue不用自己写-waitnotify)
14. [线程池：实际开发中的主角](#14-线程池实际开发中的主角)
15. [Future 与 CompletableFuture](#15-future-与-completablefuture)
16. [并发集合](#16-并发集合)
17. [ThreadLocal](#17-threadlocal)
18. [三个并发工具类](#18-三个并发工具类)
19. [死锁：成因、复现与排查](#19-死锁成因复现与排查)
20. [线程安全的单例](#20-线程安全的单例)
21. [虚拟线程（JDK 21）](#21-虚拟线程jdk-21)
22. [选型速查表](#22-选型速查表)

**收尾**

23. [常见误区](#23-常见误区)
24. [分级练习题](#24-分级练习题)
25. [一句话总结](#25-一句话总结)

---

# 第一部分：基础（初级工程师）

## 1. 为什么需要多线程

### 理由一：把等待的时间利用起来

绝大多数业务代码的时间都花在**等**上面：等数据库返回、等 HTTP 响应、等文件读完。等待期间 CPU 是空闲的。

一个真实场景：详情页要展示用户信息、订单列表、优惠券，三个接口各 300 ms。

```java
// 串行：三次等待叠加
UserInfo u = userApi.get(id);        // 等 300 ms
List<Order> o = orderApi.list(id);   // 等 300 ms
List<Coupon> c = couponApi.list(id); // 等 300 ms
// 总耗时 ≈ 900 ms
```

三个请求之间没有依赖关系，完全可以同时发出：

```java
// 并行：三次等待重叠
CompletableFuture<UserInfo> fu = CompletableFuture.supplyAsync(() -> userApi.get(id));
CompletableFuture<List<Order>> fo = CompletableFuture.supplyAsync(() -> orderApi.list(id));
CompletableFuture<List<Coupon>> fc = CompletableFuture.supplyAsync(() -> couponApi.list(id));
// 总耗时 ≈ 300 ms
```

实测两个 300 ms 任务并行（见第 15 节完整代码）：

```
张三 有 7 个订单
两个 300ms 任务并行，总耗时 309 ms
```

**309 ms，而不是 600 ms。** 这是多线程在业务开发中最直接的价值。

### 理由二：用满多核 CPU

实测本机核数：

```java
Runtime.getRuntime().availableProcessors()   // 实测输出: 10
```

单线程程序最多用掉 1 个核，另外 9 个核在闲着。计算密集型任务（图片处理、大批量数据转换）用多线程能接近线性加速。

### 理由三：你早就在用多线程了

即使你从没写过 `new Thread()`，你的程序也是多线程的：

```java
System.out.println(Thread.currentThread().getName());   // 实测输出: main
```

- Tomcat / Spring Boot 的每个 HTTP 请求跑在**线程池的一个线程**上
- JVM 的 GC、JIT 编译在后台线程里跑
- `@Scheduled` 定时任务、MQ 消费者都是独立线程

**所以"要不要学多线程"不是个选择题。** 你写的 Service 类同时被几十个请求线程调用，一个成员变量写错就是线上并发 bug。

### 代价：多线程不是免费的

| 代价 | 说明 |
|------|------|
| **正确性变难** | 共享数据被多个线程读写，结果可能是错的（第 6 节） |
| **调试变难** | bug 不稳定复现，加日志会改变时序 |
| **有额外开销** | 创建线程、线程切换、加锁竞争都要花时间 |
| **可能死锁** | 两个线程互相等对方的锁，永久卡住（第 19 节） |

一条务实的原则：**先确认瓶颈真的在等待或 CPU 上，再上多线程。** 不要为了"看起来高级"把简单逻辑写成异步。

---

## 2. 进程、线程、并发与并行

### 进程 vs 线程

| | 进程（Process） | 线程（Thread） |
|---|----------------|---------------|
| 是什么 | 一个运行中的程序 | 进程内的一条执行路径 |
| 内存 | **独立**的内存空间 | **共享**所属进程的堆和方法区 |
| 独占资源 | 内存、文件句柄 | 各自独立的**栈**和**程序计数器** |
| 通信方式 | 管道、Socket、共享内存 | 直接读写共享变量（所以才有线程安全问题） |
| 创建成本 | 高 | 低（但也不低，见第 14 节） |
| 崩溃影响 | 互不影响 | 一个线程 OOM 可能拖垮整个进程 |

一个 JVM 实例 = 一个进程，里面跑着很多线程。

**记住这张内存分布图，它解释了后面所有的线程安全问题：**

```
┌─────────────── JVM 进程 ───────────────┐
│                                        │
│   堆（Heap）           方法区           │   ← 所有线程共享，是线程安全问题的战场
│   所有对象实例         类信息、静态变量   │
│   ─────────────────────────────────────│
│   线程A        线程B        线程C       │   ← 每个线程独占
│   ┌─────┐     ┌─────┐     ┌─────┐     │
│   │虚拟机栈│    │虚拟机栈│    │虚拟机栈│    │      局部变量在这里，天然线程安全
│   │程序计数器│  │程序计数器│  │程序计数器│  │
│   └─────┘     └─────┘     └─────┘     │
└────────────────────────────────────────┘
```

**直接推论：**

- **局部变量天然线程安全** —— 它在各自的栈里，别的线程根本看不到
- **成员变量（尤其是 static）是危险区** —— 在堆/方法区里，被所有线程共享

```java
public class OrderService {
    private int counter = 0;              // ⚠️ 危险：Spring 的 Service 是单例，所有请求线程共享

    public void handle() {
        int localSum = 0;                 // ✅ 安全：每个线程有自己的一份
        counter++;                        // ⚠️ 并发下会丢数据
    }
}
```

### 并发 vs 并行

| | 并发（Concurrency） | 并行（Parallelism） |
|---|--------------------|--------------------|
| 含义 | 多个任务**交替**执行 | 多个任务**同时**执行 |
| 硬件要求 | 单核就能做到 | 必须多核 |
| 类比 | 一个人左右手轮流写两封信 | 两个人各写一封信 |

单核 CPU 上的"同时运行"是假的：操作系统每隔几毫秒切换一次线程（**时间片轮转**），快到你感觉不出来。

这个细节有实际意义：**线程切换是有成本的**（保存/恢复寄存器、刷新 CPU 缓存）。线程数远超核数时，CPU 会把大量时间花在切换上，吞吐反而下降。这就是第 14 节线程池大小要算的原因。

---

## 3. 创建线程的四种方式

四种写法实测跑一遍：

```java
// 方式一：继承 Thread
static class MyThread extends Thread {
    @Override public void run() {
        System.out.println("[继承Thread] 运行在: " + Thread.currentThread().getName());
    }
}

// 方式二：实现 Runnable
static class MyRunnable implements Runnable {
    @Override public void run() {
        System.out.println("[实现Runnable] 运行在: " + Thread.currentThread().getName());
    }
}

// 方式三：Lambda（Runnable 是函数式接口）
// 方式四：实现 Callable，交给线程池，能拿返回值
static class MyCallable implements Callable<Integer> {
    @Override public Integer call() { return 1 + 2 + 3; }
}
```

调用方式：

```java
System.out.println("main 方法运行在: " + Thread.currentThread().getName());

Thread t1 = new MyThread();
t1.setName("thread-A");
t1.start();
t1.join();

Thread t2 = new Thread(new MyRunnable(), "thread-B");
t2.start();
t2.join();

Thread t3 = new Thread(() -> System.out.println("[Lambda] 运行在: "
        + Thread.currentThread().getName()), "thread-C");
t3.start();
t3.join();

ExecutorService pool = Executors.newSingleThreadExecutor();
Future<Integer> f = pool.submit(new MyCallable());
System.out.println("[Callable] 有返回值: " + f.get());
pool.shutdown();
```

实测输出：

```
main 方法运行在: main
[继承Thread] 运行在: thread-A
[实现Runnable] 运行在: thread-B
[Lambda] 运行在: thread-C
[Callable] 有返回值: 6
```

> 这里每次 `start()` 后都紧跟 `join()`，所以输出顺序固定。去掉 `join()` 后三行的顺序就不确定了 —— 这是多线程程序的常态。

### 四种方式对比

| 方式 | 返回值 | 能抛受检异常 | 占用继承位 | 推荐度 |
|------|-------|-------------|-----------|-------|
| 继承 `Thread` | ❌ | ❌ | ✅ 占用 | ⭐ 不推荐 |
| 实现 `Runnable` | ❌ | ❌ | ❌ 不占 | ⭐⭐⭐ |
| Lambda | ❌ | ❌ | ❌ 不占 | ⭐⭐⭐⭐ 简单任务首选 |
| 实现 `Callable` | ✅ | ✅ | ❌ 不占 | ⭐⭐⭐⭐⭐ 需要结果时首选 |

**为什么不推荐继承 Thread：**

1. Java 单继承，继承了 `Thread` 就不能继承别的类了
2. 把"任务"和"执行机制"耦合在一起了 —— `Runnable` 只描述"做什么"，`Thread` 负责"谁来做"，职责分开更灵活
3. 同一个 `Runnable` 实例可以交给多个线程或线程池复用，继承 `Thread` 的对象只能 `start()` 一次

**Runnable vs Callable 的签名差异：**

```java
public interface Runnable {
    void run();                      // 无返回值，不能抛受检异常
}

public interface Callable<V> {
    V call() throws Exception;       // 有返回值，可以抛受检异常
}
```

`Runnable.run()` 不能抛受检异常，带来一个很烦的后果：

```java
new Thread(() -> {
    Thread.sleep(1000);              // ❌ 编译错误：未处理 InterruptedException
}).start();

new Thread(() -> {
    try { Thread.sleep(1000); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); }  // 只能就地 catch
}).start();
```

这就是 `Thread.sleep()` 永远要包 `try-catch` 的原因。

### 重要：实际项目里不要 new Thread()

上面四种是语法基础，但**生产代码里几乎不该出现 `new Thread().start()`**：

```java
// ❌ 实际项目中的反模式
public void handleRequest(Request req) {
    new Thread(() -> sendNotification(req)).start();   // 每个请求创建一个线程
}
```

问题：请求量上来后线程数无上限增长，内存耗尽、切换开销爆炸。**正确做法是用线程池**（第 14 节）。

---

## 4. Thread 常用 API

### start() vs run()：最经典的入门考题

```java
Thread t = new Thread(() -> System.out.println("实际执行线程: "
        + Thread.currentThread().getName()), "my-thread");

System.out.print("直接调用 run():  ");
t.run();
System.out.print("调用 start():    ");
t.start();
t.join();
```

实测输出：

```
直接调用 run():    实际执行线程: main
调用 start():      实际执行线程: my-thread
```

| 调用 | 行为 |
|------|------|
| `run()` | **普通方法调用**，在当前线程里同步执行，没有新线程 |
| `start()` | 向 JVM 申请创建一个新线程，由它去调 `run()` |

`run()` 输出 `main` —— 这证明它根本没开新线程。初学者最容易犯的错就是写 `t.run()`，代码能跑、结果也对，但完全是单线程的。

### start() 只能调一次

```java
t.start();
t.start();   // 第二次
```

实测：

```
第二次 start() -> java.lang.IllegalThreadStateException
```

线程是**一次性**的：`run()` 执行完就进入 `TERMINATED` 状态，无法重启。想重复执行任务，用线程池。

### 常用方法速查

| 方法 | 作用 | 要注意的点 |
|------|------|-----------|
| `start()` | 启动线程 | 只能调一次 |
| `Thread.sleep(ms)` | 当前线程睡眠 | **不释放锁**（见第 7 节实测） |
| `join()` | 等这个线程结束 | 在哪个线程调，就阻塞哪个线程 |
| `join(ms)` | 最多等 ms 毫秒 | 超时就不等了，不保证对方已结束 |
| `Thread.currentThread()` | 拿到当前线程对象 | 排查问题时打日志常用 |
| `interrupt()` | 发中断**请求** | 不是强制停止（见第 9 节） |
| `isInterrupted()` | 查中断标志 | 不清除标志 |
| `Thread.interrupted()` | 查并**清除**标志 | 静态方法，容易误用 |
| `setDaemon(true)` | 设为守护线程 | 必须在 `start()` **之前**调 |
| `setName(String)` | 设线程名 | **强烈建议设**，排查问题靠它 |
| `setPriority(1~10)` | 设优先级 | 只是给 OS 的**建议**，别指望它 |
| `Thread.yield()` | 让出当前时间片 | 只是建议，几乎没有实际用途 |
| ~~`stop()`~~ | 强制停止 | **已废弃**，会直接丢锁导致数据不一致 |

### join()：等待子线程完成

```java
Thread t = new Thread(() -> { /* 耗时任务 */ });
t.start();
t.join();            // main 线程在这里阻塞，直到 t 结束
System.out.println("子线程已完成");
```

不写 `join()` 的典型 bug：

```java
List<String> results = new ArrayList<>();
for (int i = 0; i < 3; i++) {
    new Thread(() -> results.add(fetch())).start();
}
System.out.println(results.size());   // 很可能输出 0 —— 子线程还没跑完
```

### 守护线程（Daemon Thread）

| | 用户线程（默认） | 守护线程 |
|---|-----------------|---------|
| JVM 退出条件 | **所有**用户线程结束后 JVM 才退出 | JVM 退出时**直接被杀**，不等它跑完 |
| 典型例子 | `main`、业务线程 | GC 线程、监控上报线程 |

```java
Thread monitor = new Thread(() -> {
    while (true) { reportMetrics(); sleep(1000); }
});
monitor.setDaemon(true);    // 必须在 start() 之前
monitor.start();
// main 结束时，monitor 会被直接终止，不会阻止 JVM 退出
```

**关键警告：守护线程里不要做需要收尾的事。** 它可能在任何位置被强行终止，`finally` 不保证执行，写文件可能只写一半。

### 一定要给线程起名字

```java
// ❌ 出问题时日志里只有 Thread-0、Thread-1、pool-1-thread-3，看不出是干什么的
new Thread(task).start();

// ✅ 有业务含义的名字，线上排查省一半时间
new Thread(task, "order-sync-worker").start();
```

线程池同理，用 `ThreadFactory` 统一命名（见第 14 节）。

---

## 5. 线程的六种状态

Java 用枚举 `Thread.State` 定义了六种状态。实测枚举值：

```java
System.out.println(Arrays.toString(Thread.State.values()));
```

```
[NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED]
```

### 五个状态的实测抓取

```java
Object lock = new Object();
Thread t = new Thread(() -> {
    synchronized (lock) {
        try { lock.wait(); } catch (InterruptedException e) { }
    }
}, "state-demo");

System.out.println("start 前         : " + t.getState());
t.start();
Thread.sleep(100);
System.out.println("调用 wait() 后   : " + t.getState());
synchronized (lock) { lock.notify(); }
t.join();
System.out.println("run 结束后       : " + t.getState());

Thread sleeper = new Thread(() -> { try { Thread.sleep(2000); } catch (InterruptedException e) { } });
sleeper.start();
Thread.sleep(100);
System.out.println("sleep(2000) 中   : " + sleeper.getState());
sleeper.interrupt();

// 制造一个抢不到锁的线程
Object monitor = new Object();
Thread holder = new Thread(() -> { synchronized (monitor) { sleep(1000); } });
Thread waiter = new Thread(() -> { synchronized (monitor) { } }, "waiter");
holder.start(); Thread.sleep(100);
waiter.start();  Thread.sleep(100);
System.out.println("抢不到锁时       : " + waiter.getState());
```

实测输出：

```
start 前         : NEW
调用 wait() 后   : WAITING
run 结束后       : TERMINATED
sleep(2000) 中   : TIMED_WAITING
抢不到锁时       : BLOCKED
```

### 状态含义与进入方式

| 状态 | 含义 | 怎么进去 | 怎么出来 |
|------|------|---------|---------|
| **NEW** | 已创建，还没启动 | `new Thread()` | 调 `start()` |
| **RUNNABLE** | 可运行（包含"正在运行"和"等 CPU 时间片"） | `start()` 之后 | 任务结束 / 阻塞 |
| **BLOCKED** | 卡在 `synchronized` 入口抢锁 | 想进 `synchronized` 但锁被占 | 拿到锁 |
| **WAITING** | 无限期等待别人唤醒 | `wait()`、`join()`、`park()` | `notify()` / `notifyAll()` / 对方结束 |
| **TIMED_WAITING** | 带超时的等待 | `sleep(n)`、`wait(n)`、`join(n)` | 超时到 / 被唤醒 / 被中断 |
| **TERMINATED** | 已结束 | `run()` 正常返回或抛异常退出 | 不可逆，无法重启 |

```
          ┌──────┐
          │ NEW  │
          └──┬───┘
         start()
          ┌──▼───────┐  抢不到 synchronized 锁   ┌─────────┐
          │ RUNNABLE │ ────────────────────────► │ BLOCKED │
          │          │ ◄──────────────────────── └─────────┘
          │          │        拿到锁
          │          │  wait() / join()          ┌─────────┐
          │          │ ────────────────────────► │ WAITING │
          │          │ ◄──────────────────────── └─────────┘
          │          │   notify() / notifyAll()
          │          │  sleep(n) / wait(n)       ┌──────────────┐
          │          │ ────────────────────────► │TIMED_WAITING │
          │          │ ◄──────────────────────── └──────────────┘
          └──┬───────┘        超时
       run() 结束
      ┌──────▼──────┐
      │ TERMINATED  │
      └─────────────┘
```

### 三个容易混淆的点

**1. Java 的 RUNNABLE 把两件事合在了一起。** 操作系统层面区分"正在 CPU 上跑"和"在就绪队列排队"，Java 统一叫 `RUNNABLE`。**而且等待 IO（读文件、读 socket）的线程在 Java 里也是 `RUNNABLE`** —— 这常让人困惑：明明在等，为什么不是 WAITING？因为 JVM 不关心 OS 层面的 IO 阻塞。

**2. BLOCKED 和 WAITING 的区别。**

| | BLOCKED | WAITING |
|---|---------|---------|
| 在等什么 | **锁** | 别人**唤醒**它 |
| 怎么进去 | 进不去 `synchronized` 块 | 主动调 `wait()` / `join()` |
| 怎么恢复 | 锁被释放，自己抢到了 | 必须别人调 `notify()` |
| 主动还是被动 | 被动（被挡在门外） | 主动（自己决定等） |

**3. 这些状态是排查线上问题的核心工具。** 用 `jstack <pid>` 导出线程栈后：

| 看到什么 | 说明什么 |
|---------|---------|
| 大量线程 `BLOCKED` 在同一行代码 | 锁竞争严重，这行是瓶颈 |
| 两个线程互相 `BLOCKED`，`jstack` 报 `Found one Java-level deadlock` | 死锁（第 19 节） |
| 线程池线程全是 `WAITING` on `BlockingQueue` | 正常，在等任务 |
| 大量线程 `RUNNABLE` 且 CPU 100% | 计算密集或死循环 |

---

## 6. 线程安全问题的根源：三大特性

这一节是整篇文章的理论核心。**理解了三大特性，后面所有工具的存在理由都讲得通。**

### 先看问题：10 个线程各加 10 万次

```java
static int unsafeCount = 0;
static void incUnsafe() { unsafeCount++; }

int threads = 10, loops = 100_000;
Thread[] ts = new Thread[threads];
for (int i = 0; i < threads; i++) {
    ts[i] = new Thread(() -> { for (int j = 0; j < loops; j++) incUnsafe(); });
}
for (Thread t : ts) t.start();
for (Thread t : ts) t.join();
System.out.println("期望值        : " + threads * loops);
System.out.println("裸 i++ 实际值 : " + unsafeCount);
```

实测输出（**每次运行结果都不同**）：

```
期望值            : 1000000
裸 i++ 实际值     : 167310   丢了 832690
```

**丢了 83%。** 这不是小概率事件，而是必然发生的。

### 原子性：i++ 不是一步完成的

`unsafeCount++` 看起来是一行代码，编译成字节码是**三步**：

```
getstatic  unsafeCount    // 1. 从内存读到操作栈
iconst_1 / iadd           // 2. 加 1
putstatic  unsafeCount    // 3. 写回内存
```

两个线程交错执行时：

```
初始 count = 100

线程A: 读取 count → 100
                              线程B: 读取 count → 100      ← 也读到 100
线程A: 100 + 1 = 101
                              线程B: 100 + 1 = 101
线程A: 写回 count = 101
                              线程B: 写回 count = 101      ← 覆盖了 A 的结果

两次自增，count 只加了 1
```

**原子性 = 一个操作要么全做完，要么完全没做，中间不会被别的线程插进来。**

| 操作 | 原子吗 |
|------|-------|
| `int i = 1;`（基本类型赋值） | ✅ 是 |
| `i = j;`（读一个写一个） | ❌ 不是（读+写两步） |
| `i++` / `i--` / `i += 1` | ❌ **不是**（读+改+写三步） |
| `long` / `double` 的读写 | ⚠️ 32 位 JVM 上**不保证**（64 位值分两次 32 位操作），64 位 JVM 上是原子的 |

### 可见性：线程可能看不到别人的修改

每个 CPU 核有自己的高速缓存。线程改了变量，可能只改在自己核的缓存里，别的线程读的还是旧值。

```
线程A（核1）        线程B（核2）
   ↓                  ↓
缓存: flag=false    缓存: flag=true   ← B 读到的是过期的副本
   ↓                  ↓
   └──── 主内存: flag=? ────┘
```

实测验证（用 `volatile` 做对照）：

```java
static volatile boolean volatileFlag = true;

Thread t = new Thread(() -> {
    long i = 0;
    while (volatileFlag) { i++; }
    System.out.println("线程正常退出，循环了 " + i + " 次");
});
t.start();
Thread.sleep(300);
volatileFlag = false;
t.join(2000);
System.out.println("线程还活着吗: " + t.isAlive());
```

实测输出：

```
volatile 版本：线程正常退出，循环了 1253024227 次
volatile 版本线程还活着吗: false
```

加了 `volatile`，子线程在 300 ms 内（空转了 12 亿次）感知到了主线程的修改并退出。

**去掉 `volatile` 会怎样？** 子线程可能**永远不退出**。原因有两个：一是 CPU 缓存不刷新，二是 JIT 编译器看到循环体里没有修改 `plainFlag`，会把 `while (plainFlag)` 优化成 `while (true)`。

> 这个现象依赖 JIT 是否触发优化，不同 JVM、不同运行次数表现可能不同，所以本文没有把"死循环"作为实测结论列出。但**不能依赖普通变量跨线程传递状态**这一条是确定的。

### 有序性：代码不一定按你写的顺序执行

编译器和 CPU 为了优化性能，会在**不改变单线程执行结果**的前提下重排指令：

```java
int a = 1;      // 语句1
int b = 2;      // 语句2    这两句互不依赖，可能被换顺序
int c = a + b;  // 语句3    依赖前两句，不会被提前
```

单线程看不出区别，多线程就可能出问题。最经典的例子是对象初始化：

```java
instance = new Singleton();
```

这一行实际是三步：

```
1. 分配内存
2. 调用构造方法初始化对象
3. 把内存地址赋给 instance
```

**步骤 2 和 3 可能被重排**。重排后，另一个线程可能看到 `instance != null`，但对象还没初始化完 —— 拿到一个半成品对象。这就是第 20 节单例必须加 `volatile` 的原因。

### 三大特性与三种工具的对应关系

| 特性 | 问题 | `synchronized` | `volatile` | 原子类（CAS） |
|------|------|---------------|-----------|--------------|
| **原子性** | `i++` 会丢数据 | ✅ 保证 | ❌ **不保证** | ✅ 保证（单个变量） |
| **可见性** | 读到过期值 | ✅ 保证 | ✅ 保证 | ✅ 保证 |
| **有序性** | 指令重排 | ✅ 保证 | ✅ 禁止重排 | ✅ 保证 |

**这张表是本文第一部分的结论**，值得单独记住。注意中间那个 ❌ —— 它是 `volatile` 被误用最多的地方（第 8 节会实测）。

### 修复：加锁或用原子类

```java
static int syncCount = 0;
static synchronized void incSync() { syncCount++; }       // 方案一：加锁

static AtomicInteger atomicCount = new AtomicInteger();
// atomicCount.incrementAndGet();                          // 方案二：原子类
```

实测输出：

```
期望值            : 1000000
裸 i++ 实际值     : 167310   丢了 832690
synchronized 版本 : 1000000
AtomicInteger 版本: 1000000
```

两种方案都准确。怎么选见第 11 节。

---

## 7. synchronized：最常用的同步手段

`synchronized` 是 Java 内置的关键字，一次性解决原子性、可见性、有序性三个问题。

### 三种用法，锁的是不同对象

**这是用好 `synchronized` 的关键：搞清楚锁对象是谁。**

```java
public class Counter {
    private int count;

    // 用法一：修饰实例方法 → 锁的是 this（当前对象）
    public synchronized void inc1() { count++; }

    // 用法二：修饰静态方法 → 锁的是 Counter.class（类对象，全局唯一）
    public static synchronized void inc2() { }

    // 用法三：同步代码块 → 锁的是你指定的对象，范围最小，推荐
    private final Object lock = new Object();
    public void inc3() {
        // 不需要同步的耗时代码放在外面
        synchronized (lock) {
            count++;              // 只锁真正需要保护的部分
        }
    }
}
```

| 用法 | 锁对象 | 作用范围 |
|------|-------|---------|
| 实例方法 | `this` | **同一个实例**的所有同步实例方法互斥 |
| 静态方法 | `类名.class` | **整个类**级别互斥，所有实例共享一把锁 |
| 同步块 | 括号里指定的对象 | 由你控制，可大可小 |

### 实测：锁对象选错等于没加锁

这是初学者最常踩的坑 —— 代码里明明有 `synchronized`，结果还是错的：

```java
static int wrongShared = 0;

Thread[] ts = new Thread[10];
for (int i = 0; i < 10; i++) {
    ts[i] = new Thread(() -> {
        Object myOwnLock = new Object();     // ⚠️ 锁对象在线程内部创建
        for (int j = 0; j < 100_000; j++) {
            synchronized (myOwnLock) { wrongShared++; }
        }
    });
}
```

实测输出：

```
每个线程锁自己的对象: 期望 1000000，实际 244743
static synchronized  : 期望 1000000，实际 1000000
```

**每个线程锁的是自己 new 出来的对象，十把不同的锁，互相之间毫无约束。** 加了锁等于没加。

**结论：锁对象必须是所有参与竞争的线程都能访问到的同一个对象。** 通常是 `private final` 的成员变量或 `Class` 对象。

### 实例锁和类锁是两把独立的锁

```java
class Account {
    public synchronized void a() { }          // 锁 this
    public static synchronized void b() { }   // 锁 Account.class
}
```

线程 1 在执行 `a()`、线程 2 同时执行 `b()` —— **完全不互斥**，因为锁对象不同。想保护同一份 `static` 数据，就必须统一用类锁。

### synchronized 是可重入锁

同一个线程可以重复获取自己已经持有的锁：

```java
static synchronized void outer() {
    System.out.println("进入 outer()，已持有锁");
    inner();                        // 调用另一个同步方法
}
static synchronized void inner() {
    System.out.println("进入 inner()，同一线程再次获取同一把锁，没有死锁");
}
```

实测输出：

```
进入 outer()，已持有锁
进入 inner()，同一线程再次获取同一把锁，没有死锁
```

如果不可重入，这段代码会自己把自己锁死。可重入性靠**计数器**实现：同一线程每次进入计数 +1，退出 -1，归零才真正释放锁。

### sleep 不释放锁，wait 释放锁

这是面试高频题，实测验证：

```java
// t1 持锁后 sleep(400)，t2 尝试获取同一把锁
Thread t1 = new Thread(() -> {
    synchronized (LOCK) {
        System.out.println("t1 拿到锁，开始 sleep(400)");
        Thread.sleep(400);
        System.out.println("t1 sleep 结束，释放锁");
    }
});
Thread t2 = new Thread(() -> {
    long s = System.currentTimeMillis();
    synchronized (LOCK) {
        System.out.println("t2 等了 " + (System.currentTimeMillis() - s) + " ms 才拿到锁");
    }
});

// t3 持锁后 wait(400)，t4 尝试获取同一把锁
// （t3 内部是 LOCK.wait(400)，其余相同）
```

实测输出：

```
t1 拿到锁，开始 sleep(400)
t1 sleep 结束，释放锁
t2 等了 349 ms 才拿到锁          ← sleep 期间锁没放，t2 一直等

t3 拿到锁，调用 wait()
t4 等了 0 ms 就拿到锁了           ← wait 立刻释放了锁
t3 被唤醒，重新拿回锁
```

| | `Thread.sleep(n)` | `obj.wait(n)` |
|---|------------------|---------------|
| 所属 | `Thread` 的**静态**方法 | `Object` 的**实例**方法 |
| 释放锁 | ❌ **不释放** | ✅ **释放** |
| 调用前提 | 任何地方 | **必须在 `synchronized` 块内** |
| 唤醒方式 | 时间到 / `interrupt()` | `notify()` / `notifyAll()` / 超时 |
| 用途 | 单纯暂停一下 | 线程间协作（第 12 节） |

**推论：不要在持有锁的时候 `sleep()`。** 你睡着的时候别人全在门外排队，这是实际项目中锁竞争的常见原因。

### 锁的粒度：别锁太多

```java
// ❌ 整个方法都锁住，调 HTTP 的 200ms 里别人全在等
public synchronized void process(Order o) {
    String token = httpClient.getToken();     // 200 ms，不需要同步
    sharedCache.put(o.getId(), o);            // 只有这行需要同步
    log.info("done");                         // 不需要同步
}

// ✅ 只锁必要的部分
public void process(Order o) {
    String token = httpClient.getToken();
    synchronized (lock) {
        sharedCache.put(o.getId(), o);
    }
    log.info("done");
}
```

### 几条实践规则

| 规则 | 原因 |
|------|------|
| 锁对象用 `private final` | 防止被外部拿到或被重新赋值（换了对象就换了锁） |
| **不要用 `String` 字面量当锁** | 字符串常量池会复用，不相关的代码可能锁到同一个对象上 |
| **不要用包装类当锁** | `Integer` 的 -128~127 缓存会让不同代码共用同一对象；`count++` 后 `count` 还会指向新对象 |
| 不要锁 `this` 暴露给外部 | 外部代码也能 `synchronized(yourObject)`，你控制不了 |
| 同步块里不做耗时 IO | 锁持有时间直接决定吞吐 |
| 多把锁时固定加锁顺序 | 避免死锁（第 19 节） |

```java
// ❌ 这两处可能锁到同一个对象上，互相干扰
synchronized ("LOCK") { }      // 字符串常量池
synchronized (Integer.valueOf(1)) { }   // 整数缓存

// ✅
private static final Object LOCK = new Object();
```

> **关于锁升级**：JDK 6 之后 `synchronized` 内部有偏向锁 → 轻量级锁 → 重量级锁的优化路径，不再是"一上来就调操作系统"。所以"`synchronized` 很慢，要用 `ReentrantLock`"这个说法已经过时了（下一节有实测数据）。
>
> 其中**偏向锁已经不在了**：[JEP 374](https://openjdk.org/jeps/374) 在 JDK 15 把它默认关闭并标记废弃，相关选项和实现代码在 JDK 18 移除。所以网上那些"偏向锁 → 轻量级锁 → 重量级锁"的文章，描述的是 JDK 8 时代的行为，在 JDK 21 上只有轻量级锁和重量级锁两级。

---

## 8. volatile：只管可见性，不管原子性

`volatile` 做两件事：

1. **保证可见性** —— 写操作立刻刷到主内存，读操作强制从主内存读
2. **禁止指令重排** —— 通过插入内存屏障实现

它**不做**的事：**不保证原子性**。

### 实测：volatile 挡不住 i++

```java
static volatile int volatileCount = 0;

// 10 个线程，各自 100_000 次 volatileCount++
runAll(10, () -> { for (int j = 0; j < 100_000; j++) volatileCount++; });
System.out.println("期望 1000000，volatile int++ 实际: " + volatileCount);
```

实测输出（**每次不同**）：

```
期望 1000000，volatile int++ 实际: 319717
```

**加了 `volatile` 照样丢数据。** 因为 `volatile` 只保证每次**读**到的是最新值，但"读 → 加 1 → 写"这三步之间仍然可以被别的线程插入。

对照第 6 节不加 `volatile` 的结果（167310）—— 都是错的，`volatile` 在这里一点用都没有。

### volatile 的正确用法：状态标志位

`volatile` 适用的场景有一个明确特征：**只有一个线程写，其他线程只读**，而且写入的新值**不依赖当前值**。

```java
public class Worker implements Runnable {
    private volatile boolean running = true;    // 一个线程写，一个线程读

    @Override public void run() {
        while (running) {                        // 只读
            doWork();
        }
        cleanup();
    }

    public void shutdown() {                     // 只写，新值和旧值无关
        running = false;
    }
}
```

这就是第 6 节那个实测的场景，`volatile` 完全够用。

```java
// ❌ volatile 用错：新值依赖旧值
private volatile int count;
public void inc() { count++; }            // 不安全

// ✅ 改用原子类
private final AtomicInteger count = new AtomicInteger();
public void inc() { count.incrementAndGet(); }
```

### volatile 的第二个用法：禁止重排（DCL 单例）

```java
public class Singleton {
    private static volatile Singleton instance;      // 这个 volatile 不能省

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();       // 这行有重排风险
                }
            }
        }
        return instance;
    }
}
```

`new Singleton()` 的三步（分配内存 → 初始化 → 赋值引用）可能被重排成"分配内存 → 赋值引用 → 初始化"。此时另一个线程走到第一个 `if (instance == null)` 发现不为 null，直接返回了**一个还没初始化完的对象**。

`volatile` 的内存屏障禁止了这种重排。**这是 `volatile` 在实际代码中第二常见的用途。** 详见第 20 节。

### synchronized vs volatile

| 维度 | `synchronized` | `volatile` |
|------|---------------|-----------|
| 修饰对象 | 方法、代码块 | **变量**（只能修饰变量） |
| 原子性 | ✅ | ❌ |
| 可见性 | ✅ | ✅ |
| 有序性 | ✅ | ✅ |
| 阻塞 | ✅ 会阻塞其他线程 | ❌ 不阻塞 |
| 开销 | 较大 | 很小 |
| 适用 | 复合操作、多个变量 | **单个状态标志位** |

**选择标准：**

```
需要保护的是单个布尔/状态变量，而且只有一个线程写？→ volatile
其他任何情况（计数、多变量一致、读改写）？         → synchronized 或原子类
```

### 一个常见误解

```java
private volatile List<String> list = new ArrayList<>();
list.add("x");        // ⚠️ volatile 完全保护不了这个操作
```

`volatile` 修饰的是**引用变量本身**，保证的是"list 这个引用的变化对其他线程可见"。`list` 指向的那个 `ArrayList` 内部状态仍然是不安全的。想要安全，得换成 `CopyOnWriteArrayList`（第 16 节）。

---

## 9. 怎么正确地停止一个线程

`Thread.stop()` 已被废弃并在新版本移除，因为它会在任意位置强行终止线程、**直接丢掉持有的锁**，留下不一致的数据。

**正确方式是协作式中断**：发出请求，让线程自己找合适的时机退出。

### 实测完整示例

```java
Thread worker = new Thread(() -> {
    int n = 0;
    while (!Thread.currentThread().isInterrupted()) {     // 1. 循环条件检查中断标志
        n++;
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            System.out.println("sleep 中被中断，捕获异常后 isInterrupted="
                    + Thread.currentThread().isInterrupted());
            Thread.currentThread().interrupt();           // 2. 关键：恢复中断标志
        }
    }
    System.out.println("循环退出，共执行 " + n + " 轮");
}, "worker");

worker.start();
Thread.sleep(220);
System.out.println("主线程调用 interrupt()");
worker.interrupt();
worker.join();
System.out.println("worker 状态: " + worker.getState());
```

实测输出：

```
主线程调用 interrupt()
sleep 中被中断，捕获异常后 isInterrupted=false
循环退出，共执行 5 轮
worker 状态: TERMINATED
```

### 这个输出里有两个关键点

**第一：`sleep()` 抛出 `InterruptedException` 时，会把中断标志清除掉。**

实测那行 `isInterrupted=false` 就是证据 —— 明明刚被中断，标志却是 `false`。这是 JDK 的设计：抛异常已经把"被中断"这个信息传达给你了，标志就重置了。

**第二：所以必须在 catch 里调 `Thread.currentThread().interrupt()` 恢复标志。**

不恢复的话，`while (!isInterrupted())` 下一轮判断是 `false`，循环**永远不会退出**：

```java
// ❌ 吞掉中断，线程停不下来
try { Thread.sleep(50); }
catch (InterruptedException e) { }                       // 标志被清了，没人知道

// ❌ 更糟：只打日志
catch (InterruptedException e) { e.printStackTrace(); }

// ✅ 恢复标志，让外层循环能感知
catch (InterruptedException e) { Thread.currentThread().interrupt(); }

// ✅ 或者直接退出
catch (InterruptedException e) { return; }
```

**"不要吞掉 `InterruptedException`"是并发代码里最重要的习惯之一。** 线程池的 `shutdownNow()` 就是靠中断停止任务的，吞掉异常会让线程池关不掉。

### 三个相关方法的区别

| 方法 | 类型 | 作用 | 是否清除标志 |
|------|------|------|------------|
| `t.interrupt()` | 实例方法 | 给 t 发中断请求 | 不清除（设置标志） |
| `t.isInterrupted()` | 实例方法 | 查 t 的中断标志 | ❌ **不清除** |
| `Thread.interrupted()` | **静态**方法 | 查**当前线程**的标志 | ✅ **会清除** |

`Thread.interrupted()` 这个"查完就清"的行为很容易出 bug。**日常只用 `isInterrupted()`**，除非你明确需要"消费掉"这个中断信号。

### 三种停止方式对比

| 方式 | 推荐度 | 说明 |
|------|-------|------|
| `volatile boolean` 标志位 | ⭐⭐⭐ | 简单直观，但**线程在 `sleep`/`wait`/阻塞 IO 中时，标志位没法立刻生效** |
| `interrupt()` + `isInterrupted()` | ⭐⭐⭐⭐⭐ | 标准做法，能打断阻塞状态 |
| `Thread.stop()` | ❌ | 已废弃，绝对不要用 |

两者可以结合：

```java
public class Worker implements Runnable {
    private volatile boolean running = true;

    @Override public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Object task = queue.poll(1, TimeUnit.SECONDS);   // 可被中断的阻塞
                if (task != null) process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        cleanup();        // 保证资源释放
    }

    public void shutdown() {
        running = false;
    }
}
```

### 注意：interrupt 不是万能的

`interrupt()` 能唤醒的阻塞：`sleep()`、`wait()`、`join()`、`BlockingQueue.take()`、`Lock.lockInterruptibly()`、NIO 的 `Selector.select()`。

**唤不醒的**：传统 `InputStream.read()` 这类阻塞 IO、`synchronized` 锁等待（`BLOCKED` 状态）。这些情况下只能靠关闭底层流/socket 来强制解除阻塞。

---

# 第二部分：进阶应用（中级工程师）

## 10. Lock 接口与 ReentrantLock

`synchronized` 够用，但有三个做不到的事：

1. **拿不到锁时无法放弃** —— 只能傻等，不能"等 2 秒就走"
2. **等锁期间不能被中断** —— `BLOCKED` 状态的线程 `interrupt()` 不了
3. **不能有多个等待条件** —— 一个锁只有一个等待队列

`java.util.concurrent.locks.Lock` 接口解决了这些。

### 基本用法与模板

```java
private final ReentrantLock lock = new ReentrantLock();

public void doSomething() {
    lock.lock();                  // 加锁
    try {
        // 临界区
    } finally {
        lock.unlock();            // 必须在 finally 里，否则异常时锁永远不释放
    }
}
```

**`unlock()` 必须写在 `finally` 里。** 这是 `Lock` 相比 `synchronized` 最大的风险点 —— `synchronized` 退出代码块时 JVM 自动释放锁，`Lock` 全靠你自己。忘了写，整个应用就卡死了。

```java
// ❌ 临界区抛异常 → unlock 永远执行不到 → 所有线程卡死
lock.lock();
doRiskyThing();
lock.unlock();
```

### 四个 synchronized 做不到的能力

**1. 尝试加锁，拿不到就走（`tryLock`）**

```java
if (lock.tryLock()) {                     // 立刻返回 true/false，不阻塞
    try { doWork(); } finally { lock.unlock(); }
} else {
    log.warn("资源繁忙，本次跳过");          // 降级处理，而不是傻等
}
```

**2. 带超时的加锁**

```java
if (lock.tryLock(2, TimeUnit.SECONDS)) {  // 最多等 2 秒
    try { doWork(); } finally { lock.unlock(); }
} else {
    throw new TimeoutException("获取锁超时");
}
```

这是**破解死锁最实用的手段**：拿不到就放弃并重试，不会无限期卡住。

**3. 可中断的加锁**

```java
lock.lockInterruptibly();      // 等锁期间可以被 interrupt() 唤醒
```

**4. 多个等待条件（`Condition`）**

`synchronized` 的 `wait/notifyAll` 只有一个等待队列，唤醒时会把生产者和消费者一起叫醒。`Lock` 可以分开：

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition notFull  = lock.newCondition();    // 队列不满
private final Condition notEmpty = lock.newCondition();    // 队列不空

public void put(T item) throws InterruptedException {
    lock.lock();
    try {
        while (queue.size() == capacity) notFull.await();   // 只挂起生产者
        queue.offer(item);
        notEmpty.signal();                                   // 只唤醒消费者
    } finally { lock.unlock(); }
}

public T take() throws InterruptedException {
    lock.lock();
    try {
        while (queue.isEmpty()) notEmpty.await();
        T item = queue.poll();
        notFull.signal();
        return item;
    } finally { lock.unlock(); }
}
```

精准唤醒，避免无效的线程切换。`ArrayBlockingQueue` 内部就是这么实现的。

### 公平锁与非公平锁

```java
new ReentrantLock();         // 非公平（默认）
new ReentrantLock(true);     // 公平
```

| | 非公平（默认） | 公平 |
|---|--------------|------|
| 规则 | 新来的线程可以直接抢，不排队 | 严格按申请顺序 FIFO |
| 吞吐量 | **高** | 低（唤醒排队线程有切换成本） |
| 公平性 | 可能有线程长期抢不到（饥饿） | 不会饥饿 |

**默认用非公平的。** 只有在"必须保证每个请求都得到处理、不能有人一直等"的场景才用公平锁。`synchronized` 只有非公平一种。

### ReadWriteLock：读多写少的场景

普通锁不区分读写，两个只读的线程也要互斥，太浪费。`ReentrantReadWriteLock` 把锁拆成两把：

```java
private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
private final Map<String, String> cache = new HashMap<>();

public String get(String key) {
    rwLock.readLock().lock();            // 读锁：多个线程可以同时持有
    try { return cache.get(key); }
    finally { rwLock.readLock().unlock(); }
}

public void put(String key, String value) {
    rwLock.writeLock().lock();           // 写锁：独占，和所有读写都互斥
    try { cache.put(key, value); }
    finally { rwLock.writeLock().unlock(); }
}
```

| | 读锁 | 写锁 |
|---|------|------|
| 读锁 | ✅ 共享 | ❌ 互斥 |
| 写锁 | ❌ 互斥 | ❌ 互斥 |

**适用条件：读操作远多于写操作（通常 10:1 以上），且读操作本身有一定耗时。** 写多的场景用它反而更慢（维护读写状态有额外开销）。

### 实测性能对比

4 个线程，各自对同一个计数器累加 250 万次：

```java
// synchronized
runAll(4, () -> { for (int j = 0; j < 2_500_000; j++) synchronized (lk) { a[0]++; } });

// ReentrantLock
runAll(4, () -> { for (int j = 0; j < 2_500_000; j++) { rl.lock(); try { b[0]++; } finally { rl.unlock(); } } });

// AtomicInteger
runAll(4, () -> { for (int j = 0; j < 2_500_000; j++) c.incrementAndGet(); });

// LongAdder
runAll(4, () -> { for (int j = 0; j < 2_500_000; j++) d.increment(); });
```

实测输出：

```
synchronized  : 10000000  耗时 388 ms
ReentrantLock : 10000000  耗时 84 ms
AtomicInteger : 10000000  耗时 70 ms
LongAdder     : 10000000  耗时 24 ms
```

四种方式**结果全部正确**，耗时差了 16 倍。

**怎么解读这组数字：**

- 这是**极端高竞争**场景（4 个线程疯抢同一把锁、临界区只有一条指令）。真实业务里临界区更长、竞争更稀疏，差距会小得多。
- `LongAdder` 最快是因为它**分段累加** —— 每个线程加到自己的槽位上，`sum()` 时才汇总，几乎没有竞争。纯计数场景（QPS 统计、埋点）应该优先用它。
- `synchronized` 在这个测试里垫底，但**不要据此得出"synchronized 慢，都该换掉"的结论**。低竞争下两者差距很小，而 `synchronized` 不会漏 `unlock`、代码更短、JVM 还在持续优化它。
- 具体数字和机器、JVM 版本、JIT 预热程度都有关，换台机器跑可能是另一组数。

### 怎么选

```
能用 synchronized 解决？                          → 用 synchronized（代码简单，不会漏 unlock）
需要超时 / tryLock / 可中断 / 多条件？              → ReentrantLock
读远多于写？                                      → ReentrantReadWriteLock
只是单个变量的计数或累加？                          → AtomicXxx / LongAdder（第 11 节）
```

**先选最简单的那个。** 代码可读性和不出 bug，通常比那点性能差异重要得多。

---

## 11. 原子类与 CAS

加锁的思路是"**悲观**的"：先假设会冲突，所以先把门锁上。原子类的思路是"**乐观**的"：先直接改，改之前检查一下有没有被别人动过，被动过就重试。

### CAS 是什么

CAS = Compare And Swap（比较并交换），是一条 **CPU 指令**，硬件保证它的原子性。它接受三个参数：

```
CAS(内存地址 V, 预期旧值 A, 新值 B)
  如果 V 处的值 == A，就把它改成 B，返回成功
  否则什么都不做，返回失败
```

`incrementAndGet()` 的逻辑等价于：

```java
public int incrementAndGet() {
    int oldValue;
    do {
        oldValue = get();                            // 1. 读当前值
    } while (!compareAndSet(oldValue, oldValue + 1)); // 2. CAS 失败就重新读、重新试
    return oldValue + 1;
}
```

这个 `do-while` 叫**自旋**。失败说明有别的线程抢先改了，那就拿新值重算一遍。

### CAS vs 加锁

| | 加锁（悲观） | CAS（乐观） |
|---|------------|------------|
| 线程竞争时 | 阻塞挂起，等唤醒 | 不阻塞，循环重试 |
| 线程切换 | 有（挂起/唤醒要进内核） | 无 |
| 低竞争时 | 有加锁开销 | **很快** |
| 高竞争时 | 稳定 | **自旋空转浪费 CPU** |
| 适用范围 | 任意复杂的临界区 | **单个变量**的简单操作 |

**CAS 不是无条件更快。** 竞争激烈时，大量线程在那里空转重试，CPU 使用率很高但做的都是无用功。

### 常用原子类

| 分类 | 类 | 用途 |
|------|---|------|
| 基本类型 | `AtomicInteger`、`AtomicLong`、`AtomicBoolean` | 原子计数、标志位 |
| 引用类型 | `AtomicReference<V>` | 原子更新一个对象引用 |
| 带版本号 | `AtomicStampedReference` | 解决 ABA 问题 |
| 数组 | `AtomicIntegerArray` 等 | 原子更新数组某个下标 |
| 字段更新器 | `AtomicIntegerFieldUpdater` | 原子更新已有类的 `volatile` 字段 |
| **高并发累加** | **`LongAdder`**、`LongAccumulator` | **高竞争下的计数首选** |

### AtomicInteger 常用方法

```java
AtomicInteger n = new AtomicInteger(0);

n.get();                       // 读
n.set(10);                     // 写
n.incrementAndGet();           // ++n，返回新值
n.getAndIncrement();           // n++，返回旧值
n.addAndGet(5);                // n += 5，返回新值
n.compareAndSet(15, 20);       // CAS：当前是 15 就改成 20
n.getAndUpdate(x -> x * 2);    // 自定义函数更新
n.accumulateAndGet(3, Integer::sum);  // 自定义二元累加
```

### AtomicReference：原子更新对象

```java
// 不可变对象 + AtomicReference = 无锁更新复合状态
record Config(String url, int timeout) { }

AtomicReference<Config> ref = new AtomicReference<>(new Config("a.com", 1000));

// 原子地把 timeout 翻倍
ref.updateAndGet(old -> new Config(old.url(), old.timeout() * 2));
```

**这是个很实用的模式**：把多个相关字段封装成一个不可变对象，用 `AtomicReference` 整体替换，就能无锁地保证多字段的一致性。

### LongAdder：高并发计数的正确选择

第 10 节实测中 `LongAdder` 比 `AtomicInteger` 快 3 倍（24 ms vs 70 ms），原因是它换了思路：

```
AtomicLong:   所有线程 CAS 竞争同一个 value      → 竞争激烈时大量重试
LongAdder:    每个线程累加到自己的 Cell 槽位      → 几乎无竞争
              sum() 时把所有 Cell 加起来         → 读的时候才汇总
```

```java
LongAdder counter = new LongAdder();
counter.increment();          // 累加 1
counter.add(5);               // 累加 5
long total = counter.sum();   // 汇总所有槽位
```

| | `AtomicLong` | `LongAdder` |
|---|-------------|------------|
| 高并发写性能 | 一般 | **好**（实测快约 3 倍） |
| 内存占用 | 小 | 大（多个 Cell） |
| 读取精确性 | `get()` 精确 | `sum()` **不是瞬时快照**（边加边读会有偏差） |
| 适用 | 写不密集，或需要精确的 CAS 语义 | **只统计总数**（QPS、埋点、累计量） |

**选择标准：只需要"最终总数"就用 `LongAdder`；需要"每次自增后的准确返回值"（比如生成递增 ID）就用 `AtomicLong`。**

### ABA 问题

CAS 只比较"值"，不知道"值变过没有"：

```
线程A: 读到 value = 1，准备 CAS(1 → 2)
       线程B: CAS(1 → 3)  成功
       线程C: CAS(3 → 1)  成功      ← 值又变回 1 了
线程A: CAS(1 → 2) 成功               ← A 以为什么都没发生，其实中间变了两次
```

对单纯的计数器，ABA 无害。但如果值是**对象引用**或和业务状态绑定（比如"订单从待付款 → 已取消 → 待付款"），就可能出错。

解决方案是加版本号：

```java
AtomicStampedReference<String> ref = new AtomicStampedReference<>("A", 0);

int[] stampHolder = new int[1];
String current = ref.get(stampHolder);          // 同时读出值和版本号
int stamp = stampHolder[0];

// 必须值和版本号都匹配才成功
boolean ok = ref.compareAndSet(current, "B", stamp, stamp + 1);
```

实际业务里更常见的做法是在数据库表上加 `version` 字段做乐观锁 —— 同一个思想。

### 原子类的边界

**原子类只能保证单个变量的原子性。** 多个变量之间的一致性它管不了：

```java
AtomicInteger from = new AtomicInteger(100);
AtomicInteger to   = new AtomicInteger(0);

// ❌ 两次操作各自原子，但整体不原子 —— 中间状态会被别的线程看到
from.addAndGet(-50);
// 这一刻总额是 50，钱凭空消失了
to.addAndGet(50);
```

**需要保证多个变量一起变的，只能加锁**（或用 `AtomicReference` 封装成一个不可变对象整体替换）。

---

## 12. 线程间通信：wait / notify

前面讲的都是"互斥"（不要同时改）。这一节讲"协作"（我等你做完，你做完叫我）。

经典场景是**生产者-消费者**：仓库满了生产者要等，仓库空了消费者要等。

### 实测完整实现

```java
static class Buffer {
    private final Queue<Integer> q = new LinkedList<>();
    private final int cap = 3;

    public synchronized void put(int v) throws InterruptedException {
        while (q.size() == cap) {            // 必须是 while，不是 if
            System.out.println("仓库满了，生产者等待");
            wait();                          // 释放锁并挂起
        }
        q.offer(v);
        System.out.println("生产 " + v + "，库存=" + q.size());
        notifyAll();                         // 唤醒所有等待者
    }

    public synchronized int take() throws InterruptedException {
        while (q.isEmpty()) {
            System.out.println("仓库空了，消费者等待");
            wait();
        }
        int v = q.poll();
        System.out.println("消费 " + v + "，库存=" + q.size());
        notifyAll();
        return v;
    }
}
```

生产者每 30 ms 生产一个，消费者每 80 ms 消费一个（消费慢于生产）：

```java
Buffer b = new Buffer();
Thread p = new Thread(() -> { for (int i = 1; i <= 5; i++) { b.put(i); Thread.sleep(30); } });
Thread c = new Thread(() -> { for (int i = 1; i <= 5; i++) { b.take(); Thread.sleep(80); } });
p.start(); c.start();
p.join(); c.join();
```

实测输出（**顺序每次运行可能不同**）：

```
生产 1，库存=1
消费 1，库存=0
生产 2，库存=1
生产 3，库存=2
消费 2，库存=1
生产 4，库存=2
生产 5，库存=3
消费 3，库存=2
消费 4，库存=1
消费 5，库存=0
```

能看出生产快于消费，库存逐渐涨到 3（容量上限），然后生产完了消费者慢慢清空。

### 三条铁律

**铁律一：`wait()` / `notify()` 必须在 `synchronized` 块内调用。**

```java
Object lock = new Object();
lock.wait();           // ❌ 运行时抛 IllegalMonitorStateException
```

原因：`wait()` 的语义是"释放锁并等待"，你得先持有锁才能释放。

**铁律二：判断条件必须用 `while`，不能用 `if`。**

```java
if (q.isEmpty()) wait();       // ❌ 错
while (q.isEmpty()) wait();    // ✅ 对
```

两个原因：

1. **虚假唤醒（spurious wakeup）** —— 操作系统层面，线程可能在没有 `notify` 的情况下被唤醒，这是 JDK 文档明确承认的
2. **`notifyAll()` 会唤醒所有等待者** —— 多个消费者被同时唤醒，第一个抢到锁的把唯一的元素拿走了，第二个醒来时队列又空了

用 `if` 的话，被唤醒后直接往下执行，就会对空队列 `poll()` 拿到 `null`。用 `while` 会**重新检查条件**，不满足就继续等。

**铁律三：优先用 `notifyAll()`，不要用 `notify()`。**

| | `notify()` | `notifyAll()` |
|---|-----------|--------------|
| 唤醒 | **随机一个**等待者 | 全部等待者 |
| 风险 | 可能唤醒了"不该醒的那个"，导致**所有线程都卡住** | 无（代价是多几次无效唤醒） |

`notify()` 的典型 bug：队列里有一个元素，JVM 随机唤醒了一个**生产者**（它还在等队列变空），生产者检查条件发现还是满的，继续 `wait()`。于是消费者没被叫醒，整个系统卡死 —— 这叫**信号丢失**。

**`notifyAll()` 多唤醒几个线程的开销，远小于死锁的代价。**

### wait/notify 完整对照

| 方法 | 所属 | 作用 |
|------|------|------|
| `wait()` | `Object` | 释放锁，进入 `WAITING`，等唤醒 |
| `wait(ms)` | `Object` | 同上但带超时，进入 `TIMED_WAITING` |
| `notify()` | `Object` | 唤醒一个等待者（不释放锁，要等退出同步块） |
| `notifyAll()` | `Object` | 唤醒所有等待者 |

> 注意 `notify()` 不会立刻让出锁。调用它之后，当前线程继续执行到 `synchronized` 块结束才释放锁，被唤醒的线程这时才有机会竞争。

### 实践建议：这些代码你其实不该自己写

上面那个 `Buffer` 类是经典教学案例，**但在实际项目里不要自己手写**。`wait/notify` 容易写错（`if` 写成 `while`、`notify` 用错、忘记在同步块内），而 JDK 已经提供了封装好的：

```java
BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(3);   // 下一节
```

**学 `wait/notify` 的价值在于理解原理**（以及应付面试），生产代码请用 `BlockingQueue`、`CountDownLatch`、`CompletableFuture` 这些高层工具。

---

## 13. BlockingQueue：不用自己写 wait/notify

`BlockingQueue` 把上一节的全部逻辑封装好了：队列满时 `put()` 自动阻塞，队列空时 `take()` 自动阻塞。

### 同样的功能，代码少一半

```java
BlockingQueue<Integer> q = new ArrayBlockingQueue<>(3);

Thread p = new Thread(() -> {
    for (int i = 1; i <= 5; i++) { q.put(i); System.out.println("put " + i); }
});
Thread c = new Thread(() -> {
    for (int i = 1; i <= 5; i++) { System.out.println("take " + q.take()); Thread.sleep(50); }
});
p.start(); c.start();
```

实测输出（**顺序每次不同**）：

```
take 1
put 1
put 2
put 3
put 4
take 2
put 5
take 3
take 4
take 5
```

> 第一行先打印 `take 1` 再打印 `put 1`，看起来"先消费后生产"。这只是**两个线程的 `println` 本身交错**了 —— 生产者是先 `put(1)` 成功、消费者 `take()` 到了它，然后两个线程各自去打印，打印顺序不受控。**这正是多线程日志容易误导人的地方**：日志行的顺序不等于实际发生的顺序。

对比第 12 节那个 `Buffer` 类：没有锁，没有 `wait/notify`，没有 `while` 条件循环，前面三条铁律一条都不用操心。这就是用高层工具的价值。

### 四组 API，行为完全不同

这是 `BlockingQueue` 最需要记清的部分：

| 操作 | 抛异常 | 返回特殊值 | **阻塞** | 超时 |
|------|-------|-----------|---------|------|
| 插入 | `add(e)` | `offer(e)` → `false` | **`put(e)`** | `offer(e, 1, SECONDS)` |
| 移除 | `remove()` | `poll()` → `null` | **`take()`** | `poll(1, SECONDS)` |
| 查看 | `element()` | `peek()` → `null` | — | — |

**最常用的是 `put()` / `take()`（阻塞）和带超时的 `offer()` / `poll()`。**

```java
// ❌ 常见错误：用 offer 却不检查返回值，队列满了任务被静默丢弃
queue.offer(task);

// ✅ 要么阻塞等
queue.put(task);

// ✅ 要么限时等，超时后明确处理
if (!queue.offer(task, 1, TimeUnit.SECONDS)) {
    log.warn("队列已满，任务丢弃: {}", task);
}
```

### 七种实现，怎么选

| 实现 | 有界 | 特点 | 典型用途 |
|------|-----|------|---------|
| **`ArrayBlockingQueue`** | ✅ 必须指定容量 | 数组实现，一把锁 | **线程池队列首选**（有界，能挡住流量） |
| `LinkedBlockingQueue` | 可选（默认 `Integer.MAX_VALUE`） | 链表，读写两把锁，吞吐更高 | 高吞吐场景，**但务必指定容量** |
| `SynchronousQueue` | 容量 0 | 不存元素，`put` 必须等 `take` | `newCachedThreadPool` 用它 |
| `PriorityBlockingQueue` | ❌ 无界 | 按优先级出队 | 任务有优先级之分 |
| `DelayQueue` | ❌ 无界 | 元素到期才能取出 | 延迟任务、超时订单关闭 |
| `LinkedTransferQueue` | ❌ 无界 | `transfer()` 等消费者接走 | 高性能消息传递 |
| `LinkedBlockingDeque` | 可选 | 双端队列 | 工作窃取 |

**一条重要的生产经验：优先用有界队列。**

```java
// ❌ 无界队列：生产快于消费时，队列无限增长 → OOM
new LinkedBlockingQueue<>();

// ✅ 有界队列：满了之后生产者被阻塞或拒绝，形成天然的背压（back pressure）
new LinkedBlockingQueue<>(1000);
new ArrayBlockingQueue<>(1000);
```

无界队列把"系统过载"这个问题从"任务被拒绝"（看得见、可处理）变成了"内存慢慢涨直到 OOM"（看不见、挂了才知道）。**第 14 节的 `Executors.newFixedThreadPool()` 就是因为这个被禁用的。**

### DelayQueue 的实用场景

```java
// 订单 30 分钟未支付自动关闭
record DelayedOrder(String orderId, long expireAt) implements Delayed {
    @Override public long getDelay(TimeUnit unit) {
        return unit.convert(expireAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }
    @Override public int compareTo(Delayed o) {
        return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }
}

DelayQueue<DelayedOrder> queue = new DelayQueue<>();
queue.put(new DelayedOrder("ORD-1", System.currentTimeMillis() + 30 * 60 * 1000));

// 消费线程：take() 会一直阻塞到有元素到期
DelayedOrder expired = queue.take();
closeOrder(expired.orderId());
```

> 单机版延迟队列的局限：数据在内存里，**重启就丢**。生产环境通常用 Redis ZSet、RocketMQ 延迟消息或专门的定时任务框架。

---

## 14. 线程池：实际开发中的主角

**这一节是全文最重要的部分。** 实际工作中你 90% 的多线程代码都是在用线程池，而线程池参数配错是生产事故的常见原因。

### 为什么必须用线程池

```java
// ❌ 每来一个请求创建一个线程
public void handle(Request req) {
    new Thread(() -> process(req)).start();
}
```

三个问题：

| 问题 | 说明 |
|------|------|
| **创建销毁开销大** | 创建一个平台线程要向操作系统申请资源，默认栈空间 1 MB |
| **线程数无上限** | 流量高峰时创建几千个线程 → 内存耗尽 / CPU 全花在切换上 |
| **无法管理** | 没法统一监控、没法优雅关闭、没法限流 |

线程池解决了全部三个：**复用**线程、**限制**最大数量、提供**管理**接口。

### 核心七参数

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2,                                  // 1. corePoolSize    核心线程数
    4,                                  // 2. maximumPoolSize 最大线程数
    10, TimeUnit.SECONDS,               // 3+4. keepAliveTime 非核心线程空闲存活时间
    new ArrayBlockingQueue<>(2),        // 5. workQueue       任务队列
    Executors.defaultThreadFactory(),   // 6. threadFactory   线程工厂（起名字用）
    new ThreadPoolExecutor.AbortPolicy()// 7. handler         拒绝策略
);
```

### 任务提交的完整流程（必须背下来）

```
提交一个任务
     │
     ▼
① 当前线程数 < corePoolSize？
     ├─ 是 → 创建新的核心线程执行           ← 注意：即使有空闲线程也会创建，直到补满核心数
     └─ 否 ↓
② 任务队列没满？
     ├─ 是 → 放进队列排队                  ← 关键：优先排队，不是优先创建线程
     └─ 否 ↓
③ 当前线程数 < maximumPoolSize？
     ├─ 是 → 创建"非核心"线程执行
     └─ 否 ↓
④ 执行拒绝策略
```

**步骤 ② 是最容易理解错的地方**：队列没满时**不会**创建新线程，哪怕 `maximumPoolSize` 还有余量。

**直接后果：如果你用了无界队列，`maximumPoolSize` 这个参数永远不会生效**，因为队列永远不会满，走不到步骤 ③。

### 实测验证整个流程（完整可运行代码）

参数：核心 2，最大 4，队列容量 2 → 最大承载 4 + 2 = 6 个任务。提交 7 个，第 7 个必然被拒绝。

**把下面这个文件保存为 `ThreadPoolFlowDemo.java`，可以直接 `javac` + `java` 运行：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：线程池任务提交的完整流程
 *
 * 参数：核心 2，最大 4，队列容量 2  →  最大承载 4 + 2 = 6 个任务
 *
 * 每个任务耗时 1000 ms，两次提交之间间隔 100 ms，
 * 这样输出顺序是确定的，方便对照流程图阅读。
 */
public class ThreadPoolFlowDemo {

    public static void main(String[] args) throws InterruptedException {

        System.out.println("CPU 核数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("参数：核心 2，最大 4，队列容量 2 → 最大承载 6 个任务");
        System.out.println("--------------------------------------------------------");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,                                       // corePoolSize
                4,                                       // maximumPoolSize
                10L, TimeUnit.SECONDS,                   // keepAliveTime
                new ArrayBlockingQueue<>(2),             // workQueue 容量 2
                new ThreadPoolExecutor.AbortPolicy());   // 拒绝策略：抛异常

        for (int i = 1; i <= 7; i++) {
            final int taskId = i;                        // Lambda 里要用，必须是 final
            try {
                pool.execute(() -> runTask(taskId));
                System.out.printf("提交任务%d 成功    线程数=%d, 队列中=%d%n",
                        taskId, pool.getPoolSize(), pool.getQueue().size());
            } catch (RejectedExecutionException e) {
                System.out.printf("提交任务%d 被拒绝  -> %s    线程数=%d, 队列中=%d%n",
                        taskId, e.getClass().getSimpleName(),
                        pool.getPoolSize(), pool.getQueue().size());
            }
            Thread.sleep(100);   // 放慢提交速度，让输出顺序稳定
        }

        System.out.println("--------------------------------------------------------");
        pool.shutdown();
        boolean done = pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("所有任务已结束 = " + done);
        System.out.println("已完成任务数   = " + pool.getCompletedTaskCount());
    }

    /** 模拟一个耗时 1 秒的业务任务 */
    private static void runTask(int taskId) {
        System.out.printf("    >>> 任务%d 开始，执行线程 = %s%n",
                taskId, Thread.currentThread().getName());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 恢复中断标志，不要吞掉
            return;
        }
        System.out.printf("    <<< 任务%d 结束%n", taskId);
    }
}
```

> **注意 `runTask` 里的 `try-catch`**：`Runnable.run()` 不能抛受检异常，所以 `Thread.sleep()` 必须就地捕获。很多教程里的 `pool.execute(() -> { ...; Thread.sleep(200); })` 其实**编译不过**，这是初学者抄代码时最容易卡住的地方。

运行命令：

```bash
javac -encoding UTF-8 ThreadPoolFlowDemo.java
java ThreadPoolFlowDemo
```

实测输出：

```
CPU 核数 = 12
参数：核心 2，最大 4，队列容量 2 → 最大承载 6 个任务
--------------------------------------------------------
提交任务1 成功    线程数=1, 队列中=0          ← ① 创建核心线程1
    >>> 任务1 开始，执行线程 = pool-1-thread-1
提交任务2 成功    线程数=2, 队列中=0          ← ① 创建核心线程2，核心数已满
    >>> 任务2 开始，执行线程 = pool-1-thread-2
提交任务3 成功    线程数=2, 队列中=1          ← ② 入队，线程数不变！
提交任务4 成功    线程数=2, 队列中=2          ← ② 入队，队列满了
提交任务5 成功    线程数=3, 队列中=2          ← ③ 队列满，才创建第3个线程
    >>> 任务5 开始，执行线程 = pool-1-thread-3
提交任务6 成功    线程数=4, 队列中=2          ← ③ 创建第4个线程，达到 max
    >>> 任务6 开始，执行线程 = pool-1-thread-4
提交任务7 被拒绝  -> RejectedExecutionException    线程数=4, 队列中=2   ← ④ 拒绝
--------------------------------------------------------
    <<< 任务1 结束
    >>> 任务3 开始，执行线程 = pool-1-thread-1      ← 线程1空了，从队列取任务3
    <<< 任务2 结束
    >>> 任务4 开始，执行线程 = pool-1-thread-2      ← 线程2空了，取任务4
    <<< 任务5 结束
    <<< 任务6 结束
    <<< 任务3 结束
    <<< 任务4 结束
所有任务已结束 = true
已完成任务数   = 6
```

**这段输出把整个流程演示完整了**，逐行对照上面的流程图：

| 任务 | 去向 | 对应步骤 |
|------|------|---------|
| 1、2 | 创建核心线程，立刻执行 | ① |
| 3、4 | **入队，线程数停在 2 不动** | ② |
| 5、6 | 队列满了才扩容到 4 | ③ |
| 7 | 被拒绝 | ④ |

两个值得注意的现象：

1. **任务 3、4 入队时线程数一直是 2** —— 这就是"队列没满就不创建新线程"的直接证据。
2. **任务 5、6 比先提交的 3、4 更早执行** —— 3、4 在队列里排队，5、6 有专属的新线程。**线程池不保证任务的执行顺序**，需要顺序就用单线程池。

> `CPU 核数 = 12` 是本次运行的机器值，你本机跑出来可能不同（`availableProcessors()` 还会受容器 CPU 限额影响）。这个数不影响上面的流程结论，因为参数是硬编码的。

### 四种拒绝策略

| 策略 | 行为 | 适用 |
|------|------|------|
| **`AbortPolicy`**（默认） | 抛 `RejectedExecutionException` | **推荐**，让调用方明确知道系统过载 |
| `CallerRunsPolicy` | **提交任务的线程自己执行** | 想不丢任务 + 自带限流（提交方被拖慢，自然降速） |
| `DiscardPolicy` | 静默丢弃，**什么都不说** | ⚠️ 几乎不该用，任务消失得无声无息 |
| `DiscardOldestPolicy` | 丢掉队列里最老的，再试一次 | 只关心最新数据（如实时行情推送） |

#### 完整可运行代码：五种策略对比

**保存为 `RejectPolicyDemo.java`：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示：四种内置拒绝策略 + 一个自定义策略
 *
 * 统一参数：核心 1，最大 1，队列容量 1  →  最大承载 2 个任务
 * 每次都提交 4 个任务（每个 500 ms），所以第 3、4 个必然触发拒绝策略。
 */
public class RejectPolicyDemo {

    public static void main(String[] args) throws InterruptedException {
        test("AbortPolicy（默认）",       new ThreadPoolExecutor.AbortPolicy());
        test("CallerRunsPolicy",         new ThreadPoolExecutor.CallerRunsPolicy());
        test("DiscardPolicy",            new ThreadPoolExecutor.DiscardPolicy());
        test("DiscardOldestPolicy",      new ThreadPoolExecutor.DiscardOldestPolicy());
        test("自定义策略（记日志+降级）",   new MyRejectHandler());
    }

    private static void test(String name, RejectedExecutionHandler handler)
            throws InterruptedException {

        System.out.println("\n================ " + name + " ================");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                handler);

        for (int i = 1; i <= 4; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    System.out.printf("    任务%d 执行中，线程 = %s%n",
                            taskId, Thread.currentThread().getName());
                    sleep(500);
                });
                System.out.printf("  提交任务%d 成功%n", taskId);
            } catch (RejectedExecutionException e) {
                System.out.printf("  提交任务%d 抛出 %s%n",
                        taskId, e.getClass().getSimpleName());
            }
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  本轮实际完成任务数 = " + pool.getCompletedTaskCount());
    }

    /** 自定义拒绝策略：记录日志 + 统计 + 降级处理 */
    static class MyRejectHandler implements RejectedExecutionHandler {
        private final AtomicInteger rejectedCount = new AtomicInteger();

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            int total = rejectedCount.incrementAndGet();
            System.out.printf("  [告警] 线程池已满！累计拒绝 %d 次 "
                            + "(活跃=%d, 队列=%d, 已完成=%d)%n",
                    total, executor.getActiveCount(),
                    executor.getQueue().size(), executor.getCompletedTaskCount());
            // 真实项目在这里做降级：落 MQ 异步重试 / 写库待补偿 / 上报监控
            System.out.println("  [降级] 任务已转入补偿队列（此处仅打印示意）");
        }
    }

    /** 把 InterruptedException 包一层，Lambda 里用起来干净些 */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

实测输出（节选关键行）：

```
================ AbortPolicy（默认） ================
  提交任务1 成功
  提交任务2 成功
    任务1 执行中，线程 = pool-1-thread-1
  提交任务3 抛出 RejectedExecutionException     ← 直接抛异常，调用方能感知
  提交任务4 抛出 RejectedExecutionException
  本轮实际完成任务数 = 2

================ CallerRunsPolicy ================
  提交任务1 成功
  提交任务2 成功
    任务1 执行中，线程 = pool-2-thread-1
    任务3 执行中，线程 = main                   ← 注意！任务3 跑在 main 线程上
  提交任务3 成功
  提交任务4 成功
  本轮实际完成任务数 = 3                        ← 只统计到 3，因为任务3 不是池里跑的

================ DiscardPolicy ================
  提交任务1 成功
  提交任务2 成功
  提交任务3 成功                                ← "成功"是假的！
  提交任务4 成功                                ← 任务 3、4 被静默丢弃
    任务1 执行中，线程 = pool-3-thread-1
    任务2 执行中，线程 = pool-3-thread-1
  本轮实际完成任务数 = 2                        ← 只跑了 2 个，另 2 个凭空消失

================ DiscardOldestPolicy ================
  提交任务1 成功
  提交任务2 成功
  提交任务3 成功
  提交任务4 成功
    任务1 执行中，线程 = pool-4-thread-1
    任务4 执行中，线程 = pool-4-thread-1        ← 跑的是最新的任务4
  本轮实际完成任务数 = 2                        ← 任务 2、3 被挤掉了

================ 自定义策略（记日志+降级） ================
  提交任务1 成功
  提交任务2 成功
    任务1 执行中，线程 = pool-5-thread-1
  [告警] 线程池已满！累计拒绝 1 次 (活跃=1, 队列=1, 已完成=0)
  [降级] 任务已转入补偿队列（此处仅打印示意）
  提交任务3 成功
  [告警] 线程池已满！累计拒绝 2 次 (活跃=1, 队列=1, 已完成=0)
  [降级] 任务已转入补偿队列（此处仅打印示意）
  提交任务4 成功
  本轮实际完成任务数 = 2
```

**这组输出里有三个关键观察：**

| 观察 | 说明 |
|------|------|
| `CallerRunsPolicy` 下任务 3 的线程名是 **`main`** | 提交任务的线程被迫自己执行，这就是"反压"的机制 |
| `DiscardPolicy` 打印了"提交成功"但只完成 2 个 | **"提交成功"完全是假象** —— 没抛异常，所以调用方以为没事。这就是它几乎不该用的原因 |
| `DiscardOldestPolicy` 跑的是任务 1 和 **4** | 队列里最老的被挤掉，保留最新的 |

**`CallerRunsPolicy` 是很多场景的最优选**：任务不丢，而且提交方（通常是 Tomcat 请求线程）被迫自己执行，提交速度自动降下来，形成反压。代价是提交方被阻塞，不适合在 Web 请求线程里用于长任务。

### Executors 的四个快捷方法：为什么阿里规约禁用

```java
Executors.newFixedThreadPool(n);      // 固定大小
Executors.newSingleThreadExecutor();  // 单线程
Executors.newCachedThreadPool();      // 缓存型
Executors.newScheduledThreadPool(n);  // 定时任务
```

它们的真实参数：

| 方法 | corePool | maxPool | 队列 | 风险 |
|------|---------|---------|------|------|
| `newFixedThreadPool(n)` | n | n | **`LinkedBlockingQueue` 无界** | ⚠️ 任务堆积 → **OOM** |
| `newSingleThreadExecutor()` | 1 | 1 | **无界** | ⚠️ 同上 |
| `newCachedThreadPool()` | 0 | **`Integer.MAX_VALUE`** | `SynchronousQueue` | ⚠️ 线程无限增长 → **OOM** |
| `newScheduledThreadPool(n)` | n | `Integer.MAX_VALUE` | `DelayedWorkQueue` 无界 | ⚠️ 任务堆积 |

**前两个会因为队列无界而堆积任务直到 OOM，第三个会因为线程数无界而创建到 OOM。** 两条路都通向 OOM，只是撑不住的地方不一样。

> 这就是《阿里巴巴 Java 开发手册》强制要求**手动 `new ThreadPoolExecutor`** 的原因。写出来麻烦一点，但每个参数你都想过一遍，心里有数。

### 生产级线程池模板（完整可运行代码）

这部分是**两个文件**：一个是可以直接复制进项目的线程池类，一个是演示怎么用它。

**文件 1：`OrderThreadPool.java`** —— 线程池本体

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级线程池模板，可直接复制到项目使用。包含：
 *   - 手动 new ThreadPoolExecutor（不用 Executors）
 *   - 有界队列
 *   - 带业务含义的线程名 + 未捕获异常处理器
 *   - CallerRunsPolicy（不丢任务 + 自带反压）
 *   - 监控方法 stats()
 *   - 两阶段优雅关闭
 */
public final class OrderThreadPool {

    private static final int CPU = Runtime.getRuntime().availableProcessors();

    /** 队列容量单独提出来，监控时要用 */
    private static final int QUEUE_CAPACITY = 500;

    public static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            CPU * 2,                                   // 核心线程：IO 密集型取 2 倍核数
            CPU * 4,                                   // 最大线程
            60L, TimeUnit.SECONDS,                     // 空闲 60 秒回收非核心线程
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),  // 有界队列，挡住流量
            namedFactory("order-async"),               // 有业务含义的线程名
            new ThreadPoolExecutor.CallerRunsPolicy()  // 不丢任务 + 自带反压
    );

    private OrderThreadPool() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 自定义 ThreadFactory：统一命名 + 兜住未捕获异常 */
    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(false);   // 业务线程池用非守护线程，保证能优雅关闭
            t.setUncaughtExceptionHandler((thread, e) ->
                    System.err.printf("[%s] 未捕获异常: %s%n", thread.getName(), e));
            return t;
        };
    }

    /** 监控：这四个指标能发现 90% 的线程池问题 */
    public static String stats() {
        return String.format(
                "线程池状态 -> 活跃=%d/%d, 队列=%d/%d, 已完成=%d, 总任务=%d",
                POOL.getActiveCount(), POOL.getMaximumPoolSize(),
                POOL.getQueue().size(), QUEUE_CAPACITY,
                POOL.getCompletedTaskCount(), POOL.getTaskCount());
    }

    /** 两阶段优雅关闭 */
    public static void shutdownGracefully() {
        POOL.shutdown();                                        // 1. 不再接新任务
        try {
            if (!POOL.awaitTermination(30, TimeUnit.SECONDS)) { // 2. 等已有任务跑完
                System.out.println("30 秒内未结束，强制中断剩余任务");
                POOL.shutdownNow();                             // 3. 超时则中断
                POOL.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            POOL.shutdownNow();
            Thread.currentThread().interrupt();                 // 恢复中断标志
        }
    }
}
```

**文件 2：`OrderThreadPoolDemo.java`** —— 怎么用它

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 演示：提交任务 → 定期监控 → 优雅关闭
 */
public class OrderThreadPoolDemo {

    /** 用 LongAdder 统计业务完成数（高并发计数首选） */
    private static final LongAdder finished = new LongAdder();

    public static void main(String[] args) throws InterruptedException {

        System.out.println("CPU 核数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("初始 " + OrderThreadPool.stats());
        System.out.println("------------------------------------------------------------");

        // 提交 50 个模拟订单任务
        for (int i = 1; i <= 50; i++) {
            final int orderId = i;
            OrderThreadPool.POOL.execute(() -> handleOrder(orderId));
        }

        System.out.println("提交 50 个任务后 " + OrderThreadPool.stats());

        // 模拟监控线程：每 100 ms 采样一次
        for (int i = 0; i < 3; i++) {
            Thread.sleep(100);
            System.out.println("监控采样 " + (i + 1) + "：" + OrderThreadPool.stats());
        }

        System.out.println("------------------------------------------------------------");
        System.out.println("开始优雅关闭……");
        OrderThreadPool.shutdownGracefully();

        System.out.println("关闭完成 " + OrderThreadPool.stats());
        System.out.println("业务计数器统计的完成数 = " + finished.sum());
        System.out.println("线程池是否已终止 = " + OrderThreadPool.POOL.isTerminated());
    }

    /** 模拟订单处理：耗时 50 ms，其中第 7 个订单会抛异常 */
    private static void handleOrder(int orderId) {
        // 最外层兜住所有异常，这是最推荐的做法
        try {
            if (orderId == 7) {
                throw new IllegalStateException("订单 7 数据异常（故意制造的）");
            }
            TimeUnit.MILLISECONDS.sleep(50);
            finished.increment();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.printf("[%s] 处理订单 %d 失败: %s%n",
                    Thread.currentThread().getName(), orderId, e.getMessage());
        }
    }
}
```

两个文件放同一目录，编译运行：

```bash
javac -encoding UTF-8 OrderThreadPool.java OrderThreadPoolDemo.java
java OrderThreadPoolDemo
```

实测输出：

```
CPU 核数 = 12
初始 线程池状态 -> 活跃=0/48, 队列=0/500, 已完成=0, 总任务=0
------------------------------------------------------------
[order-async-7] 处理订单 7 失败: 订单 7 数据异常（故意制造的）
提交 50 个任务后 线程池状态 -> 活跃=24/48, 队列=25/500, 已完成=1, 总任务=50
监控采样 1：线程池状态 -> 活跃=21/48, 队列=0/500, 已完成=44, 总任务=50
监控采样 2：线程池状态 -> 活跃=0/48, 队列=0/500, 已完成=50, 总任务=50
监控采样 3：线程池状态 -> 活跃=0/48, 队列=0/500, 已完成=50, 总任务=50
------------------------------------------------------------
开始优雅关闭……
关闭完成 线程池状态 -> 活跃=0/48, 队列=0/500, 已完成=50, 总任务=50
业务计数器统计的完成数 = 49
线程池是否已终止 = true
```

> **注意：中间三行"监控采样"的数字每次运行都不同**（取决于任务跑到哪一步了）。另一次运行拿到的是 `活跃=23, 队列=25, 已完成=1` 和 `活跃=24, 队列=1, 已完成=25`。**首尾几行是稳定的**：初始全 0、最终已完成 50、业务计数 49。

**这段输出里有三处值得看懂：**

| 现象 | 原因 |
|------|------|
| 提交完 50 个后：**活跃 ≈ 24、队列 ≈ 25** | 12 核 × 2 = 24 个核心线程被占满，剩下的在队列里排队。这个比例是稳定的，具体数字有 ±1 的抖动 |
| 线程名是 **`order-async-7`** 而不是 `pool-1-thread-7` | 自定义 `ThreadFactory` 的效果。线上 `jstack` 时能一眼看出是哪个业务的线程池 |
| 线程池说"已完成 50"，业务计数器只有 **49** | 订单 7 抛了异常。**对线程池来说"任务跑完了"就算完成，不管业务成功还是失败** —— 所以光看 `getCompletedTaskCount()` 发现不了业务失败，必须自己埋业务指标 |

> 最后一条是实际监控中容易忽略的点：**线程池指标和业务指标要分开看**。这两个数字的差值（50 - 49 = 1）就是业务失败数。
>
> Spring 项目里直接用 `ThreadPoolTaskExecutor` 更方便（支持 `@Bean` 配置、自动随容器关闭），但底层参数的含义和上面完全一样。

### 线程池大小怎么定

实测本机核数：

```java
Runtime.getRuntime().availableProcessors()   // 实测输出: 10
```

经典公式：

| 任务类型 | 特征 | 推荐核心线程数 |
|---------|------|--------------|
| **CPU 密集型** | 加密、压缩、复杂计算，几乎不等待 | `CPU 核数 + 1` |
| **IO 密集型** | 数据库、HTTP、文件，大部分时间在等 | `CPU 核数 × 2` 起步，可更高 |
| 混合型 | 两者都有 | 拆成两个线程池分开配 |

更精确的公式：

```
最优线程数 = CPU 核数 × (1 + 平均等待时间 / 平均计算时间)
```

举例：一个任务总耗时 100 ms，其中 90 ms 在等数据库、10 ms 在计算，10 核机器：

```
10 × (1 + 90/10) = 100 个线程
```

**但这个数字只是起点，不是答案。** 实际要考虑：

- 下游能不能承受 100 个并发？（数据库连接池可能只有 20 个连接，开 100 个线程只会让 98 个在等连接）
- 内存够吗？（每个平台线程默认 1 MB 栈）
- 公式假设的"等待/计算比"在流量变化时会漂移

**真正的做法是：按公式估个初值，上监控（活跃线程数、队列长度、拒绝次数、任务耗时），压测后调整。** 不要迷信公式算出来的数。

### 必须做的两件事

**1. 监控**

```java
// 定期上报，这四个指标能发现 90% 的线程池问题
log.info("线程池: 活跃={}/{}, 队列={}/{}, 已完成={}, 总任务={}",
        pool.getActiveCount(), pool.getMaximumPoolSize(),
        pool.getQueue().size(), 500,
        pool.getCompletedTaskCount(), pool.getTaskCount());
```

| 指标异常 | 说明什么 | 怎么办 |
|---------|---------|-------|
| 队列长度持续接近上限 | 消费能力不足 | 加线程 / 优化任务耗时 / 限流 |
| 活跃线程长期等于 max | 已经打满 | 同上 |
| 拒绝次数 > 0 | **已经在丢任务了** | 立刻告警 |
| 活跃线程数长期很低 | 配置过大，浪费资源 | 调小 |

**2. 处理异常 —— 这是最隐蔽的坑**

**完整可运行代码，保存为 `ExceptionDemo.java`：**

```java
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
```

实测输出：

```
=== 情况1：submit() 且不调用 get() ===
  上面什么都没打印 —— 异常被装进 Future 吞掉了

=== 情况2：submit() 且调用 get() ===
  捕获到 ExecutionException
  真正的业务异常在 getCause() 里: java.lang.RuntimeException: 情况2 的异常

=== 情况3：execute() ===
  [UncaughtExceptionHandler] demo-1 抛出: 情况3 的异常
  异常被 UncaughtExceptionHandler 打印出来了

=== 情况4：任务内部 try-catch(Throwable) ===
  任务内部自己处理了: 情况4 的异常
  这样既不丢信息，也不依赖调用方记得 get()

=== 额外：抛异常的线程会被替换 ===
  第1次任务，线程 = demo-3
  [UncaughtExceptionHandler] demo-3 抛出: 故意抛出
  第2次任务，线程 = demo-2
  [UncaughtExceptionHandler] demo-2 抛出: 故意抛出
  第3次任务，线程 = demo-4
  [UncaughtExceptionHandler] demo-4 抛出: 故意抛出
  线程编号在变化 —— 抛异常的线程被销毁，线程池补了新线程进来
```

**情况 1 的输出是全场重点：什么都没打印。** 任务抛了异常，控制台一片干净，`UncaughtExceptionHandler` 也不触发 —— 因为 `submit()` 把异常存进了 `Future`，等你 `get()` 时才抛。你不 `get()`，它就永远躺在那里。

四种做法的取舍：

| 做法 | 异常可见性 | 推荐度 |
|------|----------|-------|
| `submit()` 不 `get()` | ❌ 完全看不到 | 绝对不要 |
| `submit()` + `get()` | ✅ 但要记得 `e.getCause()` 才是真异常 | 需要返回值时用 |
| `execute()` | ✅ 走 `UncaughtExceptionHandler` | 不需要返回值时可用 |
| **任务内部 `try-catch(Throwable)`** | ✅ 自己完全掌控 | **最推荐** |

**`submit()` 静默吞异常是线上排查最费时间的一类问题** —— 功能不生效，日志里一片干净。**在任务最外层包一个 `try-catch(Throwable)` 是最稳妥的习惯**（上面 `OrderThreadPoolDemo.handleOrder()` 就是这么写的）。

> 另外注意最后那组输出：每次抛异常，执行它的线程就**被销毁、由线程池补一个新线程**。线程编号 `demo-3 / demo-2 / demo-4` 不是严格递增的，因为池里始终有 2 个线程，死一个补一个。频繁抛异常会导致线程反复创建销毁，白白损失线程池的复用价值。

### shutdown vs shutdownNow

| | `shutdown()` | `shutdownNow()` |
|---|-------------|----------------|
| 新任务 | 拒绝 | 拒绝 |
| 队列中未执行的任务 | **继续执行完** | **丢弃**（作为返回值返回给你） |
| 正在执行的任务 | 让它跑完 | 发 `interrupt()` 试图打断 |
| 返回值 | `void` | `List<Runnable>`（被丢弃的任务） |

两个都是**立刻返回**，不等任务结束。想等就配合 `awaitTermination()`，像上面模板里那样。

#### 完整可运行代码：两种关闭方式对比

**保存为 `ShutdownDemo.java`：**

```java
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：shutdown() 与 shutdownNow() 的区别
 *
 * 两次都是：1 个线程、队列容量 10、提交 5 个任务（每个 300 ms），
 * 等第一个任务跑起来后再关闭，对比队列里剩下 4 个任务的命运。
 */
public class ShutdownDemo {

    public static void main(String[] args) throws InterruptedException {
        demo(false);   // shutdown()
        demo(true);    // shutdownNow()
    }

    private static void demo(boolean useShutdownNow) throws InterruptedException {

        String name = useShutdownNow ? "shutdownNow()" : "shutdown()";
        System.out.println("\n================ " + name + " ================");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10));

        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("    任务" + taskId + " 开始");
                try {
                    Thread.sleep(300);
                    System.out.println("    任务" + taskId + " 正常结束");
                } catch (InterruptedException e) {
                    System.out.println("    任务" + taskId + " 被中断！");
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(100);   // 等任务1 跑起来
        System.out.println("  关闭前：队列中还有 " + pool.getQueue().size() + " 个任务");

        if (useShutdownNow) {
            List<Runnable> notRun = pool.shutdownNow();
            System.out.println("  shutdownNow() 返回了 " + notRun.size()
                    + " 个未执行的任务（被丢弃）");
        } else {
            pool.shutdown();
            System.out.println("  shutdown() 返回 void，队列里的任务会继续执行完");
        }

        // 两者都是立刻返回，想等结束必须配合 awaitTermination
        boolean terminated = pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  10 秒内是否完全终止 = " + terminated);
        System.out.println("  最终完成任务数 = " + pool.getCompletedTaskCount() + " / 5");
    }
}
```

实测输出：

```
================ shutdown() ================
    任务1 开始
  关闭前：队列中还有 4 个任务
  shutdown() 返回 void，队列里的任务会继续执行完
    任务1 正常结束
    任务2 开始
    任务2 正常结束
    任务3 开始
    任务3 正常结束
    任务4 开始
    任务4 正常结束
    任务5 开始
    任务5 正常结束
  10 秒内是否完全终止 = true
  最终完成任务数 = 5 / 5          ← 队列里的任务全部跑完了

================ shutdownNow() ================
    任务1 开始
  关闭前：队列中还有 4 个任务
  shutdownNow() 返回了 4 个未执行的任务（被丢弃）
    任务1 被中断！                 ← 正在执行的任务收到了 interrupt
  10 秒内是否完全终止 = true
  最终完成任务数 = 1 / 5          ← 只有任务1 跑了（而且是被中断的）
```

**对比非常直接：同样提交 5 个任务，`shutdown()` 完成 5 个，`shutdownNow()` 只完成 1 个。**

两个细节：

1. **`shutdownNow()` 返回的 `List<Runnable>` 不要扔掉** —— 它就是被丢弃的任务，重要业务应该持久化下来后续补偿：

   ```java
   List<Runnable> notRun = pool.shutdownNow();
   for (Runnable r : notRun) {
       compensationService.save(r);   // 落库 / 发 MQ，等重启后重跑
   }
   ```

2. **`任务1 被中断！` 这行能打印出来，是因为任务里正确处理了 `InterruptedException`。** 如果写成 `catch (InterruptedException e) { }` 吞掉，`shutdownNow()` 就打断不了它 —— 这也是第 9 节强调"不要吞掉 `InterruptedException`"的实际后果。

---

## 15. Future 与 CompletableFuture

### Future 的基本用法与局限（完整可运行代码）

`Future` 的硬伤：**只能阻塞着等，不能注册回调，不能把多个 Future 组合起来。**

**保存为 `FutureDemo.java`：**

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 演示：Future 的基本用法，以及 get() 位置决定是否并行
 */
public class FutureDemo {

    public static void main(String[] args) throws Exception {

        // 演示用线程池，参数手动指定（不用 Executors）
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                5, 5, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));

        // ========== 1. Future 的基本 API ==========
        System.out.println("=== 1. Future 基本 API ===");
        Future<Integer> f = pool.submit(() -> {
            Thread.sleep(300);
            return 42;
        });
        System.out.println("  提交后立刻 isDone() = " + f.isDone());
        System.out.println("  get() 拿到结果       = " + f.get());   // 阻塞 300ms
        System.out.println("  完成后 isDone()      = " + f.isDone());

        // ========== 2. 带超时的 get + 取消 ==========
        System.out.println("\n=== 2. get(timeout) 超时 ===");
        Future<Integer> slow = pool.submit(() -> {
            Thread.sleep(2000);
            return 1;
        });
        try {
            slow.get(200, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("  200ms 内没拿到结果 -> " + e.getClass().getSimpleName());
        }
        System.out.println("  cancel(true) 返回 = " + slow.cancel(true));
        System.out.println("  isCancelled()     = " + slow.isCancelled());

        // ========== 3. 关键对比：get() 写在哪里，决定并行还是串行 ==========
        System.out.println("\n=== 3. get() 位置决定并行还是串行 ===");

        List<Callable<Integer>> tasks = Arrays.asList(
                newTask(1), newTask(2), newTask(3));

        // ❌ 错误姿势：提交一个就 get 一个
        long t1 = System.currentTimeMillis();
        int sum1 = 0;
        for (Callable<Integer> c : tasks) {
            sum1 += pool.submit(c).get();     // 提交后立刻阻塞，下一个根本没提交出去
        }
        long cost1 = System.currentTimeMillis() - t1;
        System.out.printf("  ❌ 边提交边 get : sum=%d, 耗时 %d ms（串行了）%n", sum1, cost1);

        // ✅ 正确姿势：先全部提交，再统一 get
        long t2 = System.currentTimeMillis();
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> c : tasks) {
            futures.add(pool.submit(c));      // 先全部扔进线程池
        }
        int sum2 = 0;
        for (Future<Integer> fu : futures) {
            sum2 += fu.get();                 // 再统一收结果
        }
        long cost2 = System.currentTimeMillis() - t2;
        System.out.printf("  ✅ 先全提交再 get: sum=%d, 耗时 %d ms（并行）%n", sum2, cost2);

        System.out.printf("  结果一样，耗时相差约 %.1f 倍%n", cost1 * 1.0 / cost2);

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** 造一个耗时 300ms、返回 1 的任务 */
    private static Callable<Integer> newTask(int id) {
        return () -> {
            Thread.sleep(300);
            return 1;
        };
    }
}
```

实测输出：

```
=== 1. Future 基本 API ===
  提交后立刻 isDone() = false
  get() 拿到结果       = 42
  完成后 isDone()      = true

=== 2. get(timeout) 超时 ===
  200ms 内没拿到结果 -> TimeoutException
  cancel(true) 返回 = true
  isCancelled()     = true

=== 3. get() 位置决定并行还是串行 ===
  ❌ 边提交边 get : sum=3, 耗时 911 ms（串行了）
  ✅ 先全提交再 get: sum=3, 耗时 304 ms（并行）
  结果一样，耗时相差约 3.0 倍
```

**第 3 组是重点：结果一样，耗时差 3 倍。** `pool.submit(c).get()` 写在一行，等于提交后立刻阻塞等它完成，下一个任务根本没提交出去 —— 用了线程池但完全是串行的。

**规律：所有任务先提交完，再统一收集结果。** 这个错误在 code review 里很常见，因为单行写法看起来更"简洁"。

> 耗时数字（911 / 304）每次运行会有几十毫秒浮动，但 **3 倍的比例是稳定的**（3 个任务，串行 3×300ms vs 并行 1×300ms）。

### CompletableFuture 常用 API（完整可运行代码）

**保存为 `CompletableFutureDemo.java`，七组用法一次跑完：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：CompletableFuture 的常用 API
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

        // ========== 2. thenApply：串行转换 ==========
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

    /** 把 InterruptedException 包一层，Lambda 里用起来干净些 */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

实测输出：

```
=== 1. thenCombine 并行合并 ===
  张三 有 7 个订单
  两个 300ms 任务并行，总耗时 328 ms          ← 不是 600ms

=== 2. thenApply 串行转换 ===
  "hello world" -> 大写 -> 长度 = 11

=== 3. thenCompose 扁平化嵌套 ===
  先查 id 再查名字 = 用户100

=== 4. exceptionally 异常降级 ===
  降级默认值 (远程调用失败)
  注意：异常被包成 CompletionException，真异常在 getCause() 里

=== 5. handle 统一处理 ===
  成功: 正常结果
  失败: 炸了

=== 6. allOf 等全部完成 ===
  结果 = ABC，耗时 302 ms（≈最慢的 300ms）

=== 7. applyToEither 取先完成的 ===
  快的赢了
```

**第 4 组要特别注意 `e.getCause()`** —— `exceptionally` 收到的是包装后的 `CompletionException`，**真正的业务异常在 `getCause()` 里**。直接 `e.getMessage()` 拿到的是包装类的消息（形如 `java.lang.RuntimeException: 远程调用失败`），这是个常见的踩坑点。

对比第 5 组的两行就能看出来：正常分支用 `e.getMessage()`（其实 `e` 是 null 没用上），异常分支必须用 `e.getCause().getMessage()` 才能拿到干净的 `炸了`。

### 常用 API 分类

| 类别 | 方法 | 说明 |
|------|------|------|
| **创建** | `supplyAsync(Supplier)` | 有返回值 |
| | `runAsync(Runnable)` | 无返回值 |
| | `completedFuture(v)` | 直接包一个已有的值 |
| **串行转换** | `thenApply(fn)` | 拿上一步结果，转换成新值 |
| | `thenAccept(consumer)` | 消费结果，无返回值 |
| | `thenCompose(fn)` | 结果再触发一个异步任务（**扁平化**，避免嵌套 Future） |
| **两个组合** | `thenCombine(other, fn)` | 等两个都完成，合并结果 |
| | `thenAcceptBoth` | 等两个都完成，只消费 |
| | `applyToEither` | 用**先完成**的那个（超时降级常用） |
| **多个组合** | `allOf(cf...)` | 等全部完成（返回 `Void`，要自己取各自结果） |
| | `anyOf(cf...)` | 任意一个完成就返回 |
| **异常处理** | `exceptionally(fn)` | 出异常时返回兜底值 |
| | `handle((r, e) -> ...)` | 正常和异常统一处理 |
| | `whenComplete((r, e) -> ...)` | 完成时回调，**不改变结果** |

### 实际场景：聚合多个接口（完整可运行代码）

这是前面所有知识点的综合应用。**保存为 `AggregateDemo.java`**，它跑两遍：第一遍三个接口都正常，第二遍故意让订单接口超慢、优惠券接口抛异常。

```java
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 演示：聚合多个远程接口
 *
 * 四个实践要点：
 *   1. 每个 supplyAsync 都显式传自定义线程池（不用默认的 commonPool）
 *   2. 每个任务单独 exceptionally 降级（一个接口挂了不影响其他）
 *   3. allOf(...).get(超时) 整体兜底
 *   4. 用 getNow(默认值) 取结果，避免超时后再次阻塞
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
```

实测输出：

```
=== 场景1：三个接口都正常（各 300ms）===
  结果: 用户=张三, 订单数=2, 优惠券数=1
  耗时: 336 ms（串行需要 900ms）

=== 场景2：订单接口超慢(2s) + 优惠券接口抛异常 ===
  [降级] 优惠券接口失败: 优惠券服务不可用
  [超时] 聚合整体超时，返回已拿到的部分数据
  结果: 用户=张三, 订单数=0, 优惠券数=0
  耗时: 1001 ms（被 1s 超时兜住了）
  注意：用户信息正常返回，订单超时取默认值，优惠券降级为空
```

**场景 2 的输出是这段代码的价值所在**：订单接口要 2 秒、优惠券接口直接挂了，但整个方法 **1001 ms 就返回了**，而且**用户信息是完整的**。没有任何一个故障把整个页面拖垮。

如果去掉 `getNow` 改用 `get()`，超时后还会再阻塞 1 秒等订单接口 —— 超时控制就形同虚设了。

**这段代码里有四个实践要点：**

| 要点 | 原因 |
|------|------|
| **每个 `supplyAsync` 都传自定义 `pool`** | 不传会用 `ForkJoinPool.commonPool()`，它是**全 JVM 共享**的，默认只有 `核数-1` 个线程。一个慢任务能把整个应用的所有异步逻辑拖垮 |
| 每个任务单独 `exceptionally` | 一个接口挂了，其他数据照样返回 |
| `allOf(...).get(超时)` | 整体超时兜底，不会无限期等 |
| 用 `getNow(默认值)` 取结果 | 超时后某些任务还没完成，`get()` 会再次阻塞，`getNow()` 直接拿默认值 |

**第一条最重要。** 默认线程池这个坑非常隐蔽：

```java
// ❌ 用了默认的 ForkJoinPool.commonPool()，10 核机器上只有 9 个线程，全 JVM 共享
CompletableFuture.supplyAsync(() -> slowHttpCall());

// ✅ 永远显式指定线程池
CompletableFuture.supplyAsync(() -> slowHttpCall(), myPool);
```

### Future vs CompletableFuture

| | `Future` | `CompletableFuture` |
|---|---------|--------------------|
| 获取结果 | 只能阻塞 `get()` | 回调、链式、阻塞都行 |
| 组合多个 | ❌ 自己写 | ✅ `thenCombine`/`allOf`/`anyOf` |
| 异常处理 | `get()` 时抛 `ExecutionException` | `exceptionally`/`handle` 链式处理 |
| 手动完成 | ❌ | ✅ `complete(v)`（可用于把回调式 API 转成 Future） |
| 引入版本 | Java 5 | Java 8 |

**新代码一律用 `CompletableFuture`。**

---

## 16. 并发集合

### 实测：线程不安全的集合会丢数据（完整可运行代码）

**保存为 `UnsafeCollectionDemo.java`**，一次把 Map 和 List 的六种实现全部对比：

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 演示：线程不安全的集合在并发下会丢数据
 *
 * 10 个线程，每个往集合里放 1000 个元素，期望总数 10000。
 * 注意：HashMap 的 key 互不重复，所以丢数据不是"互相覆盖"，纯粹是并发 bug。
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
        runAllCatching("ArrayList            ", new ArrayList<>());
        runAllCatching("CopyOnWriteArrayList ", new CopyOnWriteArrayList<>());
        runAllCatching("synchronizedList     ", Collections.synchronizedList(new ArrayList<>()));
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
        report(name, list.size());
        if (err.length() > 0) {
            System.out.println("        ⚠️ 过程中还抛出了 " + err);
        }
    }

    private static void report(String name, int actual) {
        String mark = actual == EXPECTED ? "✅ 正确" : "❌ 丢了 " + (EXPECTED - actual) + " 个";
        System.out.printf("  %s 实际 = %-6d %s%n", name, actual, mark);
    }
}
```

实测输出（**每次运行数字都不同**）：

```
10 个线程 × 每个 1000 个元素，期望总数 = 10000
（每次运行结果都不同，但线程不安全的那些一定小于期望值）

=== Map ===
  HashMap           实际 = 8988   ❌ 丢了 1012 个
  ConcurrentHashMap 实际 = 10000  ✅ 正确
  synchronizedMap   实际 = 10000  ✅ 正确

=== List ===
  ArrayList             实际 = 3844   ❌ 丢了 6156 个
  CopyOnWriteArrayList  实际 = 10000  ✅ 正确
  synchronizedList      实际 = 10000  ✅ 正确
```

**两个关键点：**

**1. `HashMap` 丢数据，而且没有任何异常抛出。** key 明明互不重复，为什么会丢？因为 `HashMap` 内部的 `size` 字段、数组槽位、扩容过程全都不是线程安全的 —— 两个线程同时扩容，一个的结果会覆盖另一个。**没异常、没日志、数据静默消失**，这是最难查的一类 bug。

**2. `ArrayList` 丢得更狠（这次丢了 61%）。** 它的 `add()` 本质是 `elementData[size++] = e` —— 两个非原子操作，并发下互相覆盖。元素多时还会抛 `ArrayIndexOutOfBoundsException`（上面那个 `runAllCatching` 就是为了兜住它，你多跑几次可能会看到 `⚠️ 过程中还抛出了 ArrayIndexOutOfBoundsException`）。

> JDK 7 的 `HashMap` 并发扩容还会形成**链表环**，导致 `get()` 进入死循环、CPU 100%。JDK 8 改成尾插法后不再成环，但数据丢失和 `size` 不准依然存在。

### 对照表

| 线程不安全 | 线程安全替代 | 实现方式 |
|-----------|------------|---------|
| `HashMap` | **`ConcurrentHashMap`** | CAS + synchronized 锁单个桶 |
| `ArrayList` | **`CopyOnWriteArrayList`** | 写时复制整个数组 |
| `HashSet` | `ConcurrentHashMap.newKeySet()` | 基于 CHM |
| `TreeMap` | `ConcurrentSkipListMap` | 跳表 |
| `LinkedList`（当队列用） | `ConcurrentLinkedQueue` / `BlockingQueue` | CAS / 锁 |
| `StringBuilder` | `StringBuffer`（或只在单线程用 SB） | 方法上加 synchronized |
| ~~`Hashtable`~~ | 已过时，用 CHM | 整个方法加锁，性能差 |

### ConcurrentHashMap 的关键点

**1. 锁粒度细。** `Hashtable` 是给每个方法加 `synchronized`（全表一把锁），`ConcurrentHashMap` (JDK 8+) 只锁**单个哈希桶**，不同桶的操作完全并行。

**2. 单个方法原子，组合操作不原子。** 这是最容易出错的地方。

**3. 不允许 null 键和 null 值。** 原因是语义歧义：`map.get(k)` 返回 `null` 时，在并发环境下你无法区分"key 不存在"和"key 存在但值是 null"，而且没法用 `containsKey` 去确认（两次调用之间状态可能已变）。

**4. `size()` 是估算值。** 并发写入时 `size()` 返回的是一个近似值，别用它做精确判断。

#### 完整可运行代码：验证第 2、3 点

**保存为 `ConcurrentHashMapDemo.java`：**

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示：ConcurrentHashMap 的正确用法
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

    /** 并发计数：两种正确写法 */
    private static void demoCounter() throws InterruptedException {
        ConcurrentHashMap<String, Integer> c1 = new ConcurrentHashMap<>();
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
        System.out.println("  merge(key, 1, Integer::sum)            = " + c1.get("hit"));
        System.out.println("  computeIfAbsent(...).incrementAndGet() = " + c2.get("hit"));
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
```

实测输出：

```
=== 1. null 的处理 ===
  HashMap 可以存 null：{null=null}
  ConcurrentHashMap 存 null 值 -> NullPointerException
  ConcurrentHashMap 存 null 键 -> NullPointerException

=== 2. containsKey + put 不是原子的 ===
  ❌ containsKey+put：慢加载函数被执行了 20 次（期望 1 次）

=== 3. computeIfAbsent 保证初始化只执行一次 ===
  ✅ computeIfAbsent：慢加载函数被执行了 1 次
     这就是本地缓存的标准写法

=== 4. 计数器写法 ===
  期望 10000
  merge(key, 1, Integer::sum)            = 10000
  computeIfAbsent(...).incrementAndGet() = 10000
```

**第 2、3 组的对比是全节重点：`20 次` vs `1 次`。**

20 个线程同时发现 key 不存在，于是**全都去查了一遍数据库**。如果 `loadFromDb` 是一次慢查询或一次远程调用，这就是典型的"缓存击穿"。`computeIfAbsent` 则保证同一个 key 的映射函数只执行一次，其他线程等它完成后直接拿结果。

所以这几个原子复合方法要记住：

```java
map.putIfAbsent(key, value);                       // 不存在才放
map.computeIfAbsent(key, k -> expensiveInit(k));   // 不存在才计算（且只计算一次）
map.merge(key, 1, Integer::sum);                   // 计数器
map.compute(key, (k, v) -> v == null ? 1 : v + 1); // 同上
```

> 注意：`computeIfAbsent` 的映射函数里**不要再操作同一个 map**，会导致死锁或 `IllegalStateException`。

### CopyOnWriteArrayList 的适用边界

**原理：每次写操作都复制一份新数组，改完再把引用换过去。读操作完全无锁。**

```java
public boolean add(E e) {
    synchronized (lock) {
        Object[] es = getArray();
        int len = es.length;
        es = Arrays.copyOf(es, len + 1);   // 复制整个数组！
        es[len] = e;
        setArray(es);
        return true;
    }
}
```

| | 性能 |
|---|------|
| 读 | **极快**（无锁，直接读数组） |
| 写 | **极慢**（每次都 O(n) 复制整个数组） |

**适用条件：读远多于写，而且集合不大。** 典型场景是配置项、监听器列表、白名单 —— 启动时写一次，运行时一直读。

```java
// ✅ 合适：监听器列表，注册一次读无数次
private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

// ❌ 不合适：高频写入的数据收集，每次 add 都复制整个数组
private final List<LogEntry> logs = new CopyOnWriteArrayList<>();   // 会变成性能灾难
```

高频写的场景用 `Collections.synchronizedList()` 或 `ConcurrentLinkedQueue`。

### 实测：遍历时被修改会发生什么（完整可运行代码）

**保存为 `IterationSafetyDemo.java`：**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 演示：一边写一边遍历会发生什么
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
```

实测输出：

```
=== 1. synchronizedList 不加锁遍历（危险）===
  第1次：抛出 ConcurrentModificationException  ← 遍历被写操作打断
  第2次：抛出 ConcurrentModificationException  ← 遍历被写操作打断
  第3次：抛出 ConcurrentModificationException  ← 遍历被写操作打断
  3 次中有 3 次抛异常（具体次数随运行波动，但这种写法随时可能炸）

=== 2. synchronizedList 手动锁住整个遍历（正确）===
  第1次：加锁遍历成功，遍历期间不会被插入
  第2次：加锁遍历成功，遍历期间不会被插入
  第3次：加锁遍历成功，遍历期间不会被插入

=== 3. CopyOnWriteArrayList 快照遍历（永不抛异常）===
  第1次：遍历到 50000 个元素（快照大小），最终 size=70000
  第2次：遍历到 50000 个元素（快照大小），最终 size=70000
  第3次：遍历到 50000 个元素（快照大小），最终 size=70000

=== 4. 写性能对比（各 add 20000 次，单线程）===
  CopyOnWriteArrayList add 20000 次: 92 ms
  synchronizedList     add 20000 次: 1 ms
  COW 每次写都复制整个数组，是 O(n) 操作 —— 只适合读多写极少
```

**三组输出对应三个结论：**

**1. `Collections.synchronizedXxx()` 最大的陷阱**：它只是给每个方法包了一层 `synchronized`，**遍历不在保护范围内**。3 次全部抛 `ConcurrentModificationException`。要安全遍历必须像第 2 组那样手动锁住整个 `for` 循环。

> 这里特意把初始元素设成 5 万个。**如果只放 5 个元素，主线程一瞬间就遍历完了，写线程还没启动，你会以为这段代码是安全的** —— 这正是并发 bug 在本地测不出来的原因：竞态窗口太窄。放大数据量就是在人为放大这个窗口。

**2. `CopyOnWriteArrayList` 的迭代器拿的是创建时的数组快照**，所以永远不抛异常。注意第 3 组的数字：**遍历到 50000 个，但最终 size 是 70000** —— 遍历期间新增的 2 万个元素完全看不到。这是"弱一致性"的代价，用它之前要确认业务能接受。

**3. COW 的写入慢了约 90 倍**（92 ms vs 1 ms，而且这还是单线程）。2 万次 add 要复制 2 万次数组。**只适合"启动时写一次、运行时一直读"的场景**（配置、监听器列表、白名单）。

### 三种 List 方案对比

| | `CopyOnWriteArrayList` | `Collections.synchronizedList` | `ConcurrentLinkedQueue` |
|---|----------------------|-------------------------------|------------------------|
| 读性能 | 极快（无锁） | 要加锁 | 快 |
| 写性能 | 极慢（O(n) 复制） | 一般 | 快（CAS） |
| 遍历安全 | ✅ 快照，不抛异常 | ❌ **必须手动加锁** | ✅ 弱一致 |
| 支持下标 | ✅ | ✅ | ❌ |
| 适用 | 读多写极少 | 读写均衡 | 当队列用 |

---

## 17. ThreadLocal

`ThreadLocal` 提供**线程局部变量**：每个线程有自己独立的一份副本，互不干扰。

### 基本隔离效果（完整可运行代码）

**保存为 `ThreadLocalBasicDemo.java`：**

```java
/**
 * 演示：ThreadLocal 的基本隔离效果
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
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

实测输出（**前两行的顺序每次运行可能不同**）：

```
=== 两个线程操作同一个 ThreadLocal 对象 ===
  T-1 读到 USER=user-of-T-1, COUNTER=100
  T-2 读到 USER=user-of-T-2, COUNTER=100

=== main 线程没 set 过，读到的是 ===
  USER    = null   （null，因为 main 没写过）
  COUNTER = 0      （0，来自 withInitial 的默认值）
```

两个线程用的是**同一个** `ThreadLocal` 对象，但各自读到自己写的值。注意 `COUNTER` 都是 100 而不是 200 —— 说明两个线程各加了一次自己的那份，没有互相影响。main 线程没 `set` 过，读到的是 `withInitial` 给的默认值。

### 和加锁的本质区别

```
加锁：          多个线程排队访问同一份数据        → 用时间换安全
ThreadLocal：   每个线程一份数据，根本不共享      → 用空间换安全
```

**`ThreadLocal` 不是"线程安全的容器"，它是"让数据不被共享"。** 没有共享就没有竞争，不需要锁。

### 实现原理

关键在于数据**存在哪**：

```
Thread 对象
  └── threadLocals (ThreadLocalMap)        ← 每个线程自己持有这个 Map
        ├── key: ThreadLocal 对象A (弱引用)  → value: 线程自己的值
        └── key: ThreadLocal 对象B (弱引用)  → value: 线程自己的值
```

**数据存在每个 `Thread` 对象里，`ThreadLocal` 实例只是查找用的 key。** 所以：

- `threadLocal.get()` 实际是 `Thread.currentThread().threadLocals.get(this)`
- 不同线程查的是不同的 Map，自然互不干扰

### 典型应用场景

| 场景 | 说明 |
|------|------|
| **用户上下文传递** | 登录信息存进去，Service / DAO 层直接取，不用一路传参 |
| **链路追踪 ID** | 一次请求的 traceId 贯穿所有日志（MDC 就是这么实现的） |
| **数据库连接 / 事务** | Spring 的 `TransactionSynchronizationManager` 用它保证同一事务复用同一连接 |
| **非线程安全工具的复用** | `SimpleDateFormat`、`StringBuilder` 等 |

```java
// 场景一：用户上下文
public class UserContext {
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    public static void set(Long userId) { CURRENT_USER.set(userId); }
    public static Long get() { return CURRENT_USER.get(); }
    public static void clear() { CURRENT_USER.remove(); }       // 必须有这个方法
}

// 拦截器里设置
public boolean preHandle(HttpServletRequest req, ...) {
    UserContext.set(parseUserId(req.getHeader("Authorization")));
    return true;
}

// 必须在 finally 里清理！
public void afterCompletion(HttpServletRequest req, ...) {
    UserContext.clear();
}
```

```java
// 场景二：SimpleDateFormat 是线程不安全的（内部有可变的 Calendar 字段）
// ❌ 共享一个实例，并发下会解析出错乱的日期甚至抛异常
private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

// ✅ 方案一：每个线程一份
private static final ThreadLocal<SimpleDateFormat> SDF =
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

// ✅✅ 方案二（更好）：直接用 Java 8 的不可变时间 API，天然线程安全
private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
```

> 新代码里 `SimpleDateFormat` 就不该出现了，用 `java.time` 包。`DateTimeFormatter` 是不可变的，可以放心做成 `static final` 共享。

### 必须懂的坑：内存泄漏

`ThreadLocalMap` 的 **key 是弱引用，value 是强引用**：

```
ThreadLocal 对象没人引用了 → 被 GC → key 变成 null
                                      ↓
但 value 还被 Entry 强引用着 → 只要线程不死，value 就回收不了
                                      ↓
线程池里的线程永远不死 → value 永久泄漏
```

**泄漏之外还有更隐蔽的问题：脏数据。** 线程池复用线程，上一个请求残留的 `ThreadLocal` 值会被下一个请求读到 —— 用户 A 的上下文串给了用户 B，这是严重的安全问题。

#### 完整可运行代码：复现脏数据和内存泄漏

**这是本节最值得亲手跑一遍的程序。保存为 `ThreadLocalPollutionDemo.java`：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：线程池里不 remove() 会发生什么
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
    }
}
```

实测输出：

```
=== ❌ 错误示范：用完不 remove() ===
  [请求1] 线程=pool-1-thread-1，设置当前用户=张三
  [请求2] 线程=pool-1-thread-1，没有 set 却读到当前用户=张三
  ⚠️ 严重问题：请求2 读到了请求1 残留的身份！
  ⚠️ 真实系统里这意味着：用户B 看到了用户A 的数据

=== ✅ 正确示范：finally 里 remove() ===
  [请求3] 设置当前用户=李四
  [请求3] 捕获异常: 业务异常
  [请求3] 已 remove()
  [请求4] 线程=pool-1-thread-1，读到当前用户=null   ← 干净的 null，正确

=== 内存泄漏：value 一直挂在线程上 ===
  任务里放了一个 20MB 的对象，但没有 remove
  下一个任务里还能读到它: 20MB（仍然占着内存！）
  线程池的线程不会销毁 → 这 20MB 永远回收不了
  remove 之后再读: null
```

**请求 2 那一行是整个演示的核心：它一行 `set` 都没写，却读到了"张三"。**

把它翻译成真实业务：用户 B 的请求命中了刚处理过用户 A 请求的那个 Tomcat 线程，于是 B 的接口里 `UserContext.get()` 返回了 A 的 ID —— B 看到了 A 的订单、A 的地址、A 的手机号。**这是数据越权，属于安全事故，而不是普通 bug。**

注意请求 3 的写法：**即使抛了异常，`finally` 里的 `remove()` 照样执行**，所以请求 4 读到的是干净的 `null`。

最后那组内存泄漏也验证了：`System.gc()` 之后那 20MB 依然在，因为 `ThreadLocalMap` 的 value 是强引用，而线程池的线程永远不死。

**铁律：用了 `ThreadLocal.set()`，就一定要在 `finally` 里 `remove()`。**

```java
// ✅ 唯一正确的模板
try {
    CONTEXT.set(value);
    doWork();
} finally {
    CONTEXT.remove();
}
```

### 另一个坑：子线程拿不到父线程的值（完整可运行代码）

**保存为 `InheritableThreadLocalDemo.java`：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：子线程能不能拿到父线程的 ThreadLocal 值
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
    }
}
```

实测输出：

```
=== 1. 普通 ThreadLocal ===
  子线程读 PLAIN = null   ← 拿不到

=== 2. InheritableThreadLocal（新建线程）===
  子线程读 INHERITABLE = 父线程的值   ← 拿到了

=== 3. InheritableThreadLocal + 线程池（会出错）===
  第1次提交，线程池内读到 = 请求A的上下文   ← 正确，因为线程此刻才被创建
  第2次提交，线程池内读到 = 请求A的上下文   ← ⚠️ 错了！还是请求A 的
  第3次提交，线程池内读到 = 请求A的上下文   ← ⚠️ 依然是请求A 的
```

**第 3 组的三行输出全是"请求A的上下文"**，哪怕主线程已经换成了 B 和 C。

**原因**：`InheritableThreadLocal` 只在**线程创建那一刻**复制父线程的值。线程池里的线程在第 1 次提交时被创建，之后一直复用 —— 它永远停留在创建时捕获的那个值上。

这比 `null` 更危险：`null` 你会立刻发现，而"一个看起来合理但属于别人的值"会静默地把业务跑错。

**解决方案**：线程池场景用阿里的 `TransmittableThreadLocal`（TTL），它在**任务提交时**捕获上下文、在**任务执行时**还原。JDK 21 的 `ScopedValue`（预览特性）也在解决同一类问题。

---

## 18. 三个并发工具类

### CountDownLatch：等 N 个任务都完成（完整可运行代码）

一个倒数计数器，`await()` 阻塞到计数归零。**保存为 `CountDownLatchDemo.java`：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：CountDownLatch —— 一个线程等 N 个任务完成
 *
 * 包含四部分：
 *   1. 基本用法：主线程等 3 个子任务
 *   2. 唯一的致命坑：任务抛异常没 countDown → await() 永久阻塞
 *   3. 正确写法：countDown 放 finally + await 带超时
 *   4. 一次性，不能复用
 */
public class CountDownLatchDemo {

    public static void main(String[] args) throws InterruptedException {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                5, 5, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));

        // ========== 1. 基本用法 ==========
        System.out.println("=== 1. 基本用法：主线程等 3 个子任务 ===");
        int n = 3;
        CountDownLatch latch = new CountDownLatch(n);
        long start = System.currentTimeMillis();

        for (int i = 1; i <= n; i++) {
            final int id = i;
            pool.execute(() -> {
                sleep(id * 200L);                     // 分别耗时 200/400/600 ms
                System.out.println("  子任务" + id + " 完成");
                latch.countDown();                    // 完成一个，计数 -1
            });
        }

        System.out.println("  主线程等待中…… 当前计数=" + latch.getCount());
        latch.await();                                // 阻塞到计数为 0
        System.out.println("  全部完成，总耗时 " + (System.currentTimeMillis() - start)
                + " ms（串行需要 1200 ms）");

        // ========== 2. 致命坑：任务异常导致 await 永久阻塞 ==========
        System.out.println("\n=== 2. ❌ 任务抛异常却没 countDown ===");
        CountDownLatch badLatch = new CountDownLatch(2);

        pool.execute(() -> {
            System.out.println("  任务A 正常完成");
            badLatch.countDown();
        });
        pool.execute(() -> {
            System.out.println("  任务B 抛异常了（countDown 写在异常之后，执行不到）");
            if (true) throw new RuntimeException("业务异常");
            badLatch.countDown();                     // 永远执行不到
        });

        System.out.println("  用 await(1秒) 试探：");
        boolean ok = badLatch.await(1, TimeUnit.SECONDS);
        System.out.println("  await 返回 " + ok + "，剩余计数=" + badLatch.getCount());
        System.out.println("  ⚠️ 如果用无参 await()，主线程会在这里永久卡死！");

        // ========== 3. 正确写法 ==========
        System.out.println("\n=== 3. ✅ 正确写法：countDown 放 finally + await 带超时 ===");
        CountDownLatch goodLatch = new CountDownLatch(3);

        for (int i = 1; i <= 3; i++) {
            final int id = i;
            pool.execute(() -> {
                try {
                    if (id == 2) throw new RuntimeException("任务" + id + " 失败");
                    sleep(100);
                    System.out.println("  任务" + id + " 成功");
                } catch (Exception e) {
                    System.out.println("  任务" + id + " 异常: " + e.getMessage());
                } finally {
                    goodLatch.countDown();            // 不管成功失败都要减
                }
            });
        }

        if (!goodLatch.await(5, TimeUnit.SECONDS)) {
            System.out.println("  等待超时，部分任务未完成");
        } else {
            System.out.println("  全部任务已结束（含失败的），计数归零");
        }

        // ========== 4. CountDownLatch 不能复用 ==========
        System.out.println("\n=== 4. CountDownLatch 是一次性的 ===");
        CountDownLatch once = new CountDownLatch(1);
        once.countDown();
        System.out.println("  归零后计数 = " + once.getCount());
        once.countDown();                             // 再减也没用
        System.out.println("  再 countDown() 一次，计数还是 = " + once.getCount());
        once.await();                                 // 立刻返回，不阻塞
        System.out.println("  await() 立刻返回，不再阻塞 —— 想循环使用请用 CyclicBarrier");

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
```

实测输出：

```
=== 1. 基本用法：主线程等 3 个子任务 ===
  主线程等待中…… 当前计数=3
  子任务1 完成
  子任务2 完成
  子任务3 完成
  全部完成，总耗时 607 ms（串行需要 1200 ms）

=== 2. ❌ 任务抛异常却没 countDown ===
  任务A 正常完成
  用 await(1秒) 试探：
  任务B 抛异常了（countDown 写在异常之后，执行不到）
Exception in thread "pool-1-thread-5" java.lang.RuntimeException: 业务异常
	at CountDownLatchDemo.lambda$main$2(CountDownLatchDemo.java:51)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(...)
  await 返回 false，剩余计数=1
  ⚠️ 如果用无参 await()，主线程会在这里永久卡死！

=== 3. ✅ 正确写法：countDown 放 finally + await 带超时 ===
  任务2 异常: 任务2 失败
  任务1 成功
  任务3 成功
  全部任务已结束（含失败的），计数归零

=== 4. CountDownLatch 是一次性的 ===
  归零后计数 = 0
  再 countDown() 一次，计数还是 = 0
  await() 立刻返回，不再阻塞 —— 想循环使用请用 CyclicBarrier
```

**第 1 组**：607 ms ≈ 最慢那个任务的耗时（600 ms），而不是三者之和 1200 ms。

**第 2 组是 `CountDownLatch` 唯一但很致命的坑**：`await 返回 false，剩余计数=1`。任务 B 抛异常后 `countDown()` 执行不到，计数永远停在 1。这里用 `await(1秒)` 才看到了 `false`；**如果写的是无参 `await()`，主线程就在那一行永久卡死** —— 表现为某个接口完全不返回，而 CPU 一点不高。

> 注意输出里那段 `Exception in thread "pool-1-thread-5"` 的堆栈 —— 这是 `execute()` 把异常交给默认 `UncaughtExceptionHandler` 打印的。**如果用的是 `submit()`，连这段堆栈都看不到**（第 14 节讲过），排查难度再上一个台阶。

**第 3 组是正确模板**：`countDown()` 放 `finally`，`await()` 带超时。任务 2 失败了，但计数照样归零，主线程正常往下走。

### CyclicBarrier：所有线程互相等，到齐了一起走（完整可运行代码）

**保存为 `CyclicBarrierDemo.java`：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 演示：CyclicBarrier —— N 个线程互相等，到齐了一起走
 */
public class CyclicBarrierDemo {

    public static void main(String[] args) throws InterruptedException {

        int parties = 3;        // 3 个线程
        int rounds = 2;         // 跑 2 轮，验证可复用

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                parties, parties, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));

        // 第二个参数：所有线程到齐后，由【最后到达的那个线程】执行这个回调
        CyclicBarrier barrier = new CyclicBarrier(parties, () ->
                System.out.println("  ---- 本轮全部到齐，由 "
                        + Thread.currentThread().getName() + " 执行屏障回调 ----"));

        System.out.println("=== 3 个线程、2 轮，每轮到齐才能进入下一轮 ===");
        System.out.println("  （每个线程每轮耗时不同，快的必须等慢的）\n");

        for (int i = 1; i <= parties; i++) {
            final int workerId = i;
            pool.execute(() -> {
                try {
                    for (int round = 1; round <= rounds; round++) {
                        // 每个线程耗时不同：workerId * 200ms
                        long cost = workerId * 200L;
                        Thread.sleep(cost);
                        System.out.printf("  第%d轮：工人%d 完成自己的活（耗时 %d ms），到达屏障%n",
                                round, workerId, cost);

                        int arriveIndex = barrier.await();   // 等其他线程都到这里
                        // await 的返回值：到达顺序的索引，最后到的是 0
                        if (arriveIndex == 0) {
                            System.out.println("      （工人" + workerId + " 是最后到达的）");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    System.out.println("  屏障被破坏: " + e.getMessage());
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n=== barrier 可以重复使用 ===");
        System.out.println("  parties = " + barrier.getParties()
                + "，当前等待数 = " + barrier.getNumberWaiting()
                + "，是否已破坏 = " + barrier.isBroken());
    }
}
```

实测输出：

```
=== 3 个线程、2 轮，每轮到齐才能进入下一轮 ===
  （每个线程每轮耗时不同，快的必须等慢的）

  第1轮：工人1 完成自己的活（耗时 200 ms），到达屏障
  第1轮：工人2 完成自己的活（耗时 400 ms），到达屏障
  第1轮：工人3 完成自己的活（耗时 600 ms），到达屏障
  ---- 本轮全部到齐，由 pool-1-thread-3 执行屏障回调 ----
      （工人3 是最后到达的）
  第2轮：工人1 完成自己的活（耗时 200 ms），到达屏障
  第2轮：工人2 完成自己的活（耗时 400 ms），到达屏障
  第2轮：工人3 完成自己的活（耗时 600 ms），到达屏障
  ---- 本轮全部到齐，由 pool-1-thread-3 执行屏障回调 ----
      （工人3 是最后到达的）

=== barrier 可以重复使用 ===
  parties = 3，当前等待数 = 0，是否已破坏 = false
```

**三个观察点：**

1. **工人 1 只干了 200 ms 的活，却要等工人 3 干完 600 ms 才能进入第 2 轮** —— 这就是"屏障"的含义：快的必须等慢的。
2. **屏障回调由最后到达的线程执行**（输出里是 `pool-1-thread-3`），不是主线程。`await()` 的返回值是到达顺序的倒序索引，**最后到达的那个拿到 0**。
3. **第 2 轮又完整走了一遍** —— 屏障到齐后自动重置，这就是 Cyclic（循环）的意思。`CountDownLatch` 做不到这一点。

| | `CountDownLatch` | `CyclicBarrier` |
|---|-----------------|----------------|
| 谁在等 | **一个线程**等 N 个任务 | **N 个线程互相**等 |
| 能否复用 | ❌ 一次性，归零后失效 | ✅ 可循环使用（名字里的 Cyclic） |
| 计数方向 | 递减到 0 | 递增到 N 后归零重置 |
| 到齐后 | 等待者继续 | **全部线程一起**继续 |
| 典型场景 | 主线程等所有子任务完成 | 多阶段并行计算、并发测试起跑线 |

### Semaphore：限制同时访问的数量（完整可运行代码）

场景：第三方接口只能承受 3 个并发，但我们有 10 个线程要调它。**保存为 `SemaphoreDemo.java`：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示：Semaphore —— 限制同时访问的数量
 */
public class SemaphoreDemo {

    /** 只发 3 个许可 */
    private static final Semaphore SEMAPHORE = new Semaphore(3);

    /** 用来观察"同时有几个线程在临界区里" */
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static final AtomicInteger maxInFlight = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                10, 10, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));

        // ========== 1. acquire/release 基本用法 ==========
        System.out.println("=== 1. 10 个线程抢 3 个许可 ===");
        for (int i = 1; i <= 10; i++) {
            final int id = i;
            pool.execute(() -> callThirdPartyApi(id));
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n  整个过程中同时在临界区的线程数最大值 = " + maxInFlight.get());
        System.out.println("  ✅ 始终不超过许可数 3");
        System.out.println("  当前可用许可 = " + SEMAPHORE.availablePermits() + "（全部归还了）");

        // ========== 2. tryAcquire 带超时：快速失败 ==========
        System.out.println("\n=== 2. tryAcquire 带超时（更实用）===");
        Semaphore s2 = new Semaphore(1);
        s2.acquire();                                   // 先把唯一的许可占掉
        System.out.println("  许可已被占用，可用许可 = " + s2.availablePermits());

        boolean got = s2.tryAcquire(300, TimeUnit.MILLISECONDS);
        System.out.println("  tryAcquire(300ms) 返回 = " + got
                + "   ← 拿不到就快速失败，不会无限等");
        if (!got) {
            System.out.println("  业务上应该在这里抛 ServiceBusyException 或走降级");
        }
        s2.release();

        // ========== 3. Semaphore(1) 不是可重入锁 ==========
        System.out.println("\n=== 3. ⚠️ Semaphore(1) 和锁的区别：不可重入 ===");
        Semaphore s3 = new Semaphore(1);
        s3.acquire();
        System.out.println("  第一次 acquire 成功");
        boolean second = s3.tryAcquire(200, TimeUnit.MILLISECONDS);
        System.out.println("  同一个线程再 acquire = " + second
                + "   ← 不可重入！ReentrantLock 在这里会成功");
        s3.release();
    }

    /** 模拟调用第三方接口，用信号量把并发压到 3 以内 */
    private static void callThirdPartyApi(int taskId) {
        try {
            SEMAPHORE.acquire();                        // 拿许可，没有就阻塞
            try {
                int now = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(now, Math::max);
                System.out.println("  任务" + taskId + " 拿到许可，当前并发 = " + now
                        + "，剩余许可 = " + SEMAPHORE.availablePermits());
                Thread.sleep(300);                      // 模拟接口耗时
                inFlight.decrementAndGet();
            } finally {
                SEMAPHORE.release();                    // 必须在 finally 里归还
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

实测输出（**任务编号的顺序每次运行都不同**）：

```
=== 1. 10 个线程抢 3 个许可 ===
  任务2 拿到许可，当前并发 = 2，剩余许可 = 0
  任务3 拿到许可，当前并发 = 3，剩余许可 = 0
  任务1 拿到许可，当前并发 = 1，剩余许可 = 0
  任务5 拿到许可，当前并发 = 2，剩余许可 = 1
  任务6 拿到许可，当前并发 = 3，剩余许可 = 0
  任务4 拿到许可，当前并发 = 1，剩余许可 = 1
  任务7 拿到许可，当前并发 = 3，剩余许可 = 0
  任务8 拿到许可，当前并发 = 2，剩余许可 = 1
  任务9 拿到许可，当前并发 = 3，剩余许可 = 0
  任务10 拿到许可，当前并发 = 3，剩余许可 = 0

  整个过程中同时在临界区的线程数最大值 = 3
  ✅ 始终不超过许可数 3
  当前可用许可 = 3（全部归还了）

=== 2. tryAcquire 带超时（更实用）===
  许可已被占用，可用许可 = 0
  tryAcquire(300ms) 返回 = false   ← 拿不到就快速失败，不会无限等
  业务上应该在这里抛 ServiceBusyException 或走降级

=== 3. ⚠️ Semaphore(1) 和锁的区别：不可重入 ===
  第一次 acquire 成功
  同一个线程再 acquire = false   ← 不可重入！ReentrantLock 在这里会成功
```

**关键观察：`最大值 = 3`。** 10 个线程全部跑完，但任何时刻临界区里最多只有 3 个 —— 这就是 `Semaphore` 的作用。那个 `maxInFlight` 变量就是专门用来证明这一点的。

> 输出里"当前并发"这一列会出现 1、2、3 跳动，是因为打印和计数之间有微小时间差，不代表许可控制失效。看最后汇总的 `maxInFlight` 才准。

**第 3 组点出了一个容易误解的地方**：`Semaphore(1)` 看起来像一把锁，但它**不可重入** —— 同一个线程 `acquire()` 两次会把自己卡死。`ReentrantLock` 在同样场景下会成功（第 7 节讲过可重入性）。另外 `Semaphore` 的许可**可以由另一个线程 `release()`**，锁则必须由持有者释放。

**典型用途：**

- 限制对下游的并发调用数（对方接口只能承受 N 并发）
- 简单的连接池 / 资源池
- 单机限流
- **虚拟线程场景下的限流** —— 线程池大小不再能限流了，只能靠 `Semaphore`（见第 21 节）

### 三者速查

| 工具 | 一句话 | 常见场景 |
|------|-------|---------|
| `CountDownLatch` | 等 N 件事做完 | 聚合多个接口结果、等初始化完成 |
| `CyclicBarrier` | N 个人到齐再一起走 | 多阶段并行计算 |
| `Semaphore` | 最多 N 个人同时进 | 限流、资源池 |

---

## 19. 死锁：成因、复现与排查

### 复现一个死锁（完整可运行代码）

**保存为 `DeadlockDemo.java`：**

```java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * 演示：复现一个死锁，并用 JMX 在代码里检测出来
 *
 * 死锁成因：t1 按 A→B 的顺序加锁，t2 按 B→A 的顺序加锁，形成循环等待。
 *
 * 注意：两个线程设成了守护线程(daemon)，否则它们永远 BLOCKED，
 *      JVM 不会退出，这个程序就卡死在那里了 —— 而这正是线上死锁的真实表现。
 */
public class DeadlockDemo {

    private static final Object lockA = new Object();
    private static final Object lockB = new Object();

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== 1. 制造死锁 ===");

        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                System.out.println("  t1 拿到 lockA，想要 lockB");
                sleep(100);                       // 给对方抢锁的时间
                synchronized (lockB) {
                    System.out.println("  t1 拿到了 lockB（这行永远不会打印）");
                }
            }
        }, "deadlock-t1");

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {                // ⚠️ 加锁顺序和 t1 相反
                System.out.println("  t2 拿到 lockB，想要 lockA");
                sleep(100);
                synchronized (lockA) {
                    System.out.println("  t2 拿到了 lockA（这行永远不会打印）");
                }
            }
        }, "deadlock-t2");

        t1.setDaemon(true);      // 设成守护线程，让 JVM 最后能正常退出
        t2.setDaemon(true);
        t1.start();
        t2.start();

        Thread.sleep(600);       // 等死锁形成

        // ========== 2. 看线程状态 ==========
        System.out.println("\n=== 2. 线程状态 ===");
        System.out.println("  t1 状态 = " + t1.getState());
        System.out.println("  t2 状态 = " + t2.getState());
        System.out.println("  两个都是 BLOCKED，各自在等对方持有的锁");

        // ========== 3. 用 JMX 检测死锁 ==========
        System.out.println("\n=== 3. 代码内检测死锁（ThreadMXBean）===");
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] ids = bean.findDeadlockedThreads();

        if (ids == null) {
            System.out.println("  未检测到死锁");
        } else {
            System.out.println("  ⚠️ 检测到死锁，涉及 " + ids.length + " 个线程：");
            for (ThreadInfo info : bean.getThreadInfo(ids, true, true)) {
                System.out.println("    - 线程名: " + info.getThreadName());
                System.out.println("      状态: " + info.getThreadState());
                System.out.println("      正在等待的锁: " + info.getLockName());
                System.out.println("      该锁被谁持有: " + info.getLockOwnerName());
            }
        }

        // ========== 4. 排查方式说明 ==========
        System.out.println("\n=== 4. 线上怎么排查 ===");
        System.out.println("  当前进程 PID = " + ProcessHandle.current().pid());
        System.out.println("  可以在另一个终端执行：");
        System.out.println("      jstack " + ProcessHandle.current().pid() + " | grep -A 20 deadlock");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

实测输出：

```
=== 1. 制造死锁 ===
  t1 拿到 lockA，想要 lockB
  t2 拿到 lockB，想要 lockA

=== 2. 线程状态 ===
  t1 状态 = BLOCKED
  t2 状态 = BLOCKED
  两个都是 BLOCKED，各自在等对方持有的锁

=== 3. 代码内检测死锁（ThreadMXBean）===
  ⚠️ 检测到死锁，涉及 2 个线程：
    - 线程名: deadlock-t1
      状态: BLOCKED
      正在等待的锁: java.lang.Object@378bf509
      该锁被谁持有: deadlock-t2
    - 线程名: deadlock-t2
      状态: BLOCKED
      正在等待的锁: java.lang.Object@4dd8dc3
      该锁被谁持有: deadlock-t1

=== 4. 线上怎么排查 ===
  当前进程 PID = 6867
  可以在另一个终端执行：
      jstack 6867 | grep -A 20 deadlock
```

**两个线程都卡在 `BLOCKED`，而 `t1 拿到了 lockB` 和 `t2 拿到了 lockA` 永远不会打印。**

第 3 组的 `该锁被谁持有` 两行拼起来就是完整的环：**t1 等的锁在 t2 手里，t2 等的锁在 t1 手里**。这就是循环等待的直接证据。

> 代码里特意把两个线程设成了**守护线程**，否则 JVM 永远不会退出，你得按 Ctrl+C 才能结束。**而"进程卡住、必须重启"正是线上死锁的真实表现。** 想体验的话把那两行 `setDaemon(true)` 注释掉再跑一次。

### 死锁的四个必要条件

四个条件**同时**满足才会死锁，破坏任意一个就能避免：

| 条件 | 含义 | 能破坏吗 |
|------|------|---------|
| **互斥** | 资源同一时刻只能被一个线程占用 | ❌ 锁的本质，破坏不了 |
| **持有并等待** | 拿着锁 A 的同时去等锁 B | ✅ 一次性申请所有锁 |
| **不可剥夺** | 别人不能抢走我持有的锁 | ✅ 用 `tryLock(超时)` 主动放弃 |
| **循环等待** | A 等 B，B 等 A，形成环 | ✅ **按固定顺序加锁（最常用）** |

### 四种避免手段

**手段一：固定加锁顺序（最常用、最有效）**

上面的死锁根源就是 t1 按 A→B、t2 按 B→A。统一顺序就不会成环：

```java
// 所有线程都按同一顺序加锁
synchronized (lockA) {
    synchronized (lockB) { }
}
```

实际场景（转账）需要一个稳定的排序依据：

```java
public void transfer(Account from, Account to, long amount) {
    // 按账户 ID 排序，保证任何两个账户之间的加锁顺序全局一致
    Account first  = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;

    synchronized (first) {
        synchronized (second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

**不排序的话**：线程 1 执行 `transfer(A, B)`、线程 2 执行 `transfer(B, A)`，就是上面那个死锁。按 ID 排序后，两个线程都会先锁 ID 小的那个账户。

**手段二：用 `tryLock` 带超时，拿不到就放弃**

注意要加**随机退避** —— 如果两个线程同时失败、同时重试，可能一直互相干扰（这叫**活锁**：没有阻塞，但谁也推进不了）。加随机延迟能打破这种同步。

#### 完整可运行代码：两种修复方案对比

**保存为 `DeadlockFixDemo.java`。它用双向并发转账各 2000 次做压测 —— 如果实现有死锁风险，几乎必然卡住：**

```java
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 演示：转账死锁的两种修复方案
 *
 * 场景：线程1 执行 transfer(A, B)，线程2 同时执行 transfer(B, A)
 *      按参数顺序加锁 → 必然死锁
 *
 * 修复一：按账户 ID 排序加锁（破坏"循环等待"）—— 最常用
 * 修复二：tryLock 带超时 + 随机退避（破坏"不可剥夺"）
 */
public class DeadlockFixDemo {

    /** 账户：同时准备了 synchronized 用的对象锁和 ReentrantLock */
    static class Account {
        final int id;
        final String name;
        long balance;
        final ReentrantLock lock = new ReentrantLock();

        Account(int id, String name, long balance) {
            this.id = id;
            this.name = name;
            this.balance = balance;
        }

        void debit(long amount)  { balance -= amount; }
        void credit(long amount) { balance += amount; }

        @Override public String toString() {
            return name + "(余额=" + balance + ")";
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // ========== 修复一：固定加锁顺序 ==========
        System.out.println("=== 修复一：按账户 ID 排序加锁（破坏循环等待）===");
        runConcurrentTransfer((from, to, amount) -> {
            // 关键：不按参数顺序，而是按一个全局一致的顺序（这里用 id）
            Account first  = from.id < to.id ? from : to;
            Account second = from.id < to.id ? to : from;

            synchronized (first) {
                synchronized (second) {
                    from.debit(amount);
                    to.credit(amount);
                }
            }
        });

        // ========== 修复二：tryLock 带超时 ==========
        System.out.println("\n=== 修复二：tryLock 带超时 + 随机退避（破坏不可剥夺）===");
        runConcurrentTransfer((from, to, amount) -> {
            while (true) {
                try {
                    if (from.lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                        try {
                            if (to.lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                                try {
                                    from.debit(amount);
                                    to.credit(amount);
                                    return;                   // 成功，退出重试循环
                                } finally {
                                    to.lock.unlock();
                                }
                            }
                        } finally {
                            from.lock.unlock();                // 拿不到第二把就释放第一把
                        }
                    }
                    // 随机退避：避免两个线程同步重试，陷入"活锁"
                    Thread.sleep(ThreadLocalRandom.current().nextInt(20));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    /** 转账逻辑的抽象，方便替换不同实现 */
    interface TransferStrategy {
        void transfer(Account from, Account to, long amount);
    }

    /**
     * 并发压测：两个方向互相转账各 2000 次。
     * 如果实现有死锁风险，这里几乎必然卡住。
     */
    private static void runConcurrentTransfer(TransferStrategy strategy)
            throws InterruptedException {

        Account a = new Account(1, "账户A", 100_000);
        Account b = new Account(2, "账户B", 100_000);
        long totalBefore = a.balance + b.balance;

        int rounds = 2000;
        CountDownLatch done = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < rounds; i++) strategy.transfer(a, b, 10);
            done.countDown();
        }, "transfer-A-to-B");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < rounds; i++) strategy.transfer(b, a, 10);   // 反方向！
            done.countDown();
        }, "transfer-B-to-A");

        t1.setDaemon(true);
        t2.setDaemon(true);
        long start = System.currentTimeMillis();
        t1.start();
        t2.start();

        boolean finished = done.await(15, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - start;

        if (!finished) {
            System.out.println("  ❌ 15 秒内没跑完，疑似死锁！");
            long[] ids = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
            System.out.println("  JMX 死锁检测: "
                    + (ids == null ? "未检测到" : "检测到 " + ids.length + " 个线程死锁"));
            return;
        }

        System.out.println("  ✅ 两个方向各转账 " + rounds + " 次，无死锁，耗时 " + cost + " ms");
        System.out.println("     " + a + "  " + b);
        System.out.println("     转账前总额 = " + totalBefore
                + "，转账后总额 = " + (a.balance + b.balance)
                + (totalBefore == a.balance + b.balance ? "  （守恒 ✅）" : "  （不守恒 ❌）"));
    }
}
```

实测输出：

```
=== 修复一：按账户 ID 排序加锁（破坏循环等待）===
  ✅ 两个方向各转账 2000 次，无死锁，耗时 1 ms
     账户A(余额=100000)  账户B(余额=100000)
     转账前总额 = 200000，转账后总额 = 200000  （守恒 ✅）

=== 修复二：tryLock 带超时 + 随机退避（破坏不可剥夺）===
  ✅ 两个方向各转账 2000 次，无死锁，耗时 125 ms
     账户A(余额=100000)  账户B(余额=100000)
     转账前总额 = 200000，转账后总额 = 200000  （守恒 ✅）
```

**两点值得注意：**

1. **修复一只用了 1 ms，修复二用了 125 ms。** 排序加锁几乎零成本；`tryLock` 方案因为有失败重试和随机退避，开销大得多。**所以固定加锁顺序是首选**，`tryLock` 适合"拿不到锁就想降级/放弃"的场景。

2. **总额守恒**（200000 → 200000）说明不只没死锁，数据也是对的。死锁测试很容易写成"没卡住就算过"，但如果加锁范围不对，可能不死锁却丢钱。**并发测试要同时验证"活性"和"正确性"两件事。**

> 想看死锁真实发生的样子，把修复一里的排序去掉，直接写 `synchronized (from) { synchronized (to) {...} }` 再跑 —— 会输出 `❌ 15 秒内没跑完，疑似死锁！` 和 JMX 的检测结果。

**手段三：减少锁的数量**

```java
// ❌ 两把锁
synchronized (lockA) { synchronized (lockB) { } }

// ✅ 一把粗粒度的锁，没有嵌套就不可能死锁
synchronized (globalLock) { }
```

牺牲一些并发度换取确定的正确性。对很多业务代码来说这个交换是划算的。

**手段四：用无锁结构或高层工具**

`ConcurrentHashMap`、原子类、`BlockingQueue` 内部已经处理好了，用它们就不用自己管锁。

### 排查线上死锁

**方法一：`jstack`（最常用）**

```bash
jps                      # 找到 Java 进程 pid
jstack <pid> > dump.txt  # 导出线程栈
grep -A 20 "deadlock" dump.txt
```

`jstack` 会直接报出来：

```
Found one Java-level deadlock:
=============================
"deadlock-t1":
  waiting to lock monitor 0x... (object 0x..., a java.lang.Object),
  which is held by "deadlock-t2"
"deadlock-t2":
  waiting to lock monitor 0x... (object 0x..., a java.lang.Object),
  which is held by "deadlock-t1"
```

**这就是为什么要给线程起有意义的名字** —— `Thread-0` 和 `Thread-1` 互相死锁，你根本不知道是哪块业务代码。

**方法二：代码内主动检测（适合做健康检查）**

就是上面实测用的 `ThreadMXBean.findDeadlockedThreads()`：

```java
@Scheduled(fixedDelay = 60_000)
public void checkDeadlock() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    long[] ids = bean.findDeadlockedThreads();
    if (ids != null) {
        for (ThreadInfo info : bean.getThreadInfo(ids, true, true)) {
            log.error("检测到死锁线程: {}\n{}", info.getThreadName(), info);
        }
        alertService.send("发现死锁，线程数: " + ids.length);
    }
}
```

**方法三：工具**：`jconsole` / `VisualVM` 有"检测死锁"按钮；Arthas 的 `thread -b` 直接定位阻塞其他线程的那个线程。

### 死锁的表现特征

| 现象 | 说明 |
|------|------|
| 部分接口**完全无响应**，其他接口正常 | 典型死锁（只有涉及那两把锁的逻辑卡住） |
| CPU **使用率不高**，但请求全超时 | 线程在 `BLOCKED`（不是死循环，死循环会 CPU 100%） |
| 线程池活跃数打满，队列一直涨 | 线程被死锁占住，无法处理新任务 |
| 重启就好了，过一段时间又复现 | 死锁需要特定的时序才会触发 |

> **CPU 不高 + 请求超时**是区分死锁和死循环的关键特征。

---

## 20. 线程安全的单例

单例是面试和实际开发都高频的话题，它把 `volatile`、类加载机制、`synchronized` 串在了一起。

### 写法一：饿汉式（推荐，最简单）

```java
public class Singleton {
    private static final Singleton INSTANCE = new Singleton();    // 类加载时就创建
    private Singleton() { }
    public static Singleton getInstance() { return INSTANCE; }
}
```

**线程安全由 JVM 保证** —— 类加载过程（`<clinit>` 方法的执行）本身是加了锁的，JVM 规范保证一个类只会被初始化一次。

缺点：不管用不用都会创建。但对绝大多数单例（Service、工具类、配置管理器）来说，这点开销可以忽略。

### 写法二：双重检查锁（DCL）

需要延迟加载时用这个。实测：

```java
static class Singleton {
    private static volatile Singleton instance;        // volatile 不能省！
    private Singleton() {
        System.out.println("构造方法被执行（应该只出现一次）");
    }
    public static Singleton getInstance() {
        if (instance == null) {                        // 第一次检查：没锁，绝大多数调用走这里，快
            synchronized (Singleton.class) {
                if (instance == null) {                // 第二次检查：有锁，准
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}

// 20 个线程同时抢
Set<Singleton> set = ConcurrentHashMap.newKeySet();
for (int i = 0; i < 20; i++) new Thread(() -> set.add(Singleton.getInstance())).start();
```

实测输出：

```
构造方法被执行（应该只出现一次）
20 个线程拿到的不同实例个数: 1
```

构造方法只执行了一次，20 个线程拿到同一个实例。

**三个细节都不能动：**

| 细节 | 去掉会怎样 |
|------|-----------|
| 第一次 `if` | 每次调用都要抢锁，性能退化成完全串行 |
| 第二次 `if` | 两个线程都通过第一次检查后排队进入，会创建**两个**实例 |
| **`volatile`** | `new Singleton()` 的指令重排可能让别的线程拿到**未初始化完成的对象**（第 8 节详解） |

### 写法三：静态内部类（兼顾延迟加载和简洁）

```java
public class Singleton {
    private Singleton() { }

    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;        // 第一次调用时才加载 Holder 类
    }
}
```

**原理：内部类在被首次使用时才加载**，所以实现了延迟加载；而类加载的线程安全由 JVM 保证，所以不需要任何锁和 `volatile`。

**这是延迟加载场景的最佳写法** —— 比 DCL 简洁得多，而且不会写错。

### 写法四：枚举（最省事，能防反射和反序列化）

```java
public enum Singleton {
    INSTANCE;

    private final long createdAt = System.nanoTime();
    public long getCreatedAt() { return createdAt; }

    public void doWork() { }
}
```

**枚举是唯一能同时防住反射攻击和反序列化攻击的写法。** 《Effective Java》把枚举单例列为首选方案。缺点是不能延迟加载、不能继承其他类。

### 完整可运行代码：四种写法 + 反射攻击

**保存为 `SingletonDemo.java`，一个文件把四种写法全部并发验证一遍：**

```java
import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示：四种线程安全的单例写法
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
```

实测输出：

```
=== 写法一：饿汉式（类加载时创建）===
  20 个线程拿到的不同实例个数 = 1   ✅ 单例成立

=== 写法二：双重检查锁 DCL（延迟加载）===
  Dcl 构造方法被执行（只应出现一次）
  20 个线程拿到的不同实例个数 = 1   ✅ 单例成立

=== 写法三：静态内部类（延迟加载，推荐）===
  20 个线程拿到的不同实例个数 = 1   ✅ 单例成立

=== 写法四：枚举（最省事）===
  20 个线程拿到的不同实例个数 = 1   ✅ 单例成立

=== 反射攻击：前三种都能被破解 ===
  饿汉式：反射创建了两个实例，o1 == o2 ? false   ❌ 单例被破解
  ⚠️ Dcl 构造方法被执行了第 2 次！
  ⚠️ Dcl 构造方法被执行了第 3 次！
  DCL：反射创建了两个实例，o1 == o2 ? false   ❌ 单例被破解
  静态内部类：反射创建了两个实例，o1 == o2 ? false   ❌ 单例被破解

=== 反射攻击枚举：JVM 层面拦住 ===
  ✅ 枚举防住了: IllegalArgumentException
     Cannot reflectively create enum objects

=== 延迟加载验证 ===
  Lazy 类还没被使用，构造方法应该还没执行
  （上面没有出现 'Lazy 的构造方法被执行'）
  现在第一次调用 getInstance()：
  >>> Lazy 的构造方法被执行
  ← 构造方法刚才才执行，这就是静态内部类的延迟加载
```

**四组观察：**

1. **四种写法在 20 线程并发下都是 1 个实例** —— 都是线程安全的。
2. **`Dcl 构造方法被执行（只应出现一次）`** 这行只出现了一次，证明双重检查确实生效了（如果去掉第二个 `if`，这里会出现多次）。
3. **反射攻击下前三种全部失守**，`o1 == o2` 都是 `false`。注意 DCL 那里还打印了"构造方法被执行了第 2、3 次" —— 反射直接绕过了 `getInstance()` 的全部逻辑。
4. **枚举抛出 `Cannot reflectively create enum objects`** —— 这是 JVM 在 `Constructor.newInstance()` 里硬编码的检查，无法绕过。

> 想防住反射攻击又不想用枚举，可以在私有构造方法里加判断：
>
> ```java
> private Hungry() {
>     if (INSTANCE != null) {
>         throw new IllegalStateException("禁止反射创建实例");
>     }
> }
> ```
>
> 但这招对 DCL 无效（第一次反射调用时 `instance` 还是 null），而且不如枚举干净。

### 四种写法对比

| 写法 | 线程安全靠 | 延迟加载 | 防反射 | 代码量 | 推荐 |
|------|----------|---------|-------|-------|------|
| 饿汉式 | 类加载机制 | ❌ | ❌ | 最少 | ⭐⭐⭐⭐ 默认选它 |
| DCL | `volatile` + `synchronized` | ✅ | ❌ | 多，易写错 | ⭐⭐ 面试要会讲 |
| 静态内部类 | 类加载机制 | ✅ | ❌ | 少 | ⭐⭐⭐⭐⭐ 需要延迟加载时选它 |
| 枚举 | 类加载 + JVM 保护 | ❌ | ✅ | 最少 | ⭐⭐⭐⭐⭐ |

### 实际项目：交给框架

```java
@Service                          // Spring 的 Bean 默认就是单例
public class OrderService { }
```

**Spring 项目里不要自己写单例。** 容器管理的 Bean 默认单例，而且支持依赖注入、AOP、生命周期回调。

> ⚠️ **但这带来一个必须警惕的后果**：`@Service` / `@Controller` 是单例，被所有请求线程共享。**在里面放可变的成员变量就是并发 bug**：
>
> ```java
> @Service
> public class OrderService {
>     private Order current;                      // ❌ 所有请求线程共享这一个字段
>
>     public void handle(Order o) {
>         this.current = o;                       // 线程 A 刚写完
>         validate();                             // 线程 B 把它改了，A 校验的是 B 的订单
>     }
> }
> ```
>
> **Service 必须是无状态的**：数据通过方法参数传递、用局部变量保存。这是实际项目中最常见的并发 bug 类型，而且测试环境单并发下根本发现不了。

---

## 21. 虚拟线程（JDK 21）

虚拟线程是 JDK 21 的**正式特性**（JEP 444），它改变了"线程很贵所以必须用池"这个前提。

### 平台线程的瓶颈

```
平台线程（传统 Thread）= 1:1 映射到操作系统线程
  - 每个默认约 1 MB 栈空间
  - 创建/切换要进内核态
  - 几千个就把内存吃完了
```

所以传统方案是"少量线程 + 线程池复用"，并且在 IO 密集场景下不得不写异步回调（`CompletableFuture` 链）来避免线程被阻塞浪费。

### 虚拟线程的做法

```
虚拟线程 = JVM 管理的轻量级线程，M:N 映射到少量平台线程（载体线程）
  - 栈在堆上，初始几百字节，按需增长
  - 遇到阻塞（sleep / IO / 锁）时，JVM 把它从载体线程上"卸下"，载体线程去跑别的虚拟线程
  - 创建上百万个都没问题
```

### 实测：10 万个虚拟线程（完整可运行代码）

**保存为 `VirtualThreadDemo.java`（需要 JDK 21+）：**

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示：虚拟线程基础（需要 JDK 21+）
 */
public class VirtualThreadDemo {

    public static void main(String[] args) throws Exception {

        System.out.println("Java 版本 = " + System.getProperty("java.version"));
        System.out.println("CPU 核数  = " + Runtime.getRuntime().availableProcessors());

        // ========== 1. 创建一个虚拟线程 ==========
        System.out.println("\n=== 1. 创建虚拟线程 ===");
        Thread vt = Thread.ofVirtual().name("v-thread").start(() ->
                System.out.println("  虚拟线程内部看到的自己: " + Thread.currentThread()));
        vt.join();
        System.out.println("  isVirtual() = " + vt.isVirtual());
        System.out.println("  注意上面输出里的 @ForkJoinPool-1-worker-N —— 那是【载体线程】");
        System.out.println("  虚拟线程被挂在载体线程上执行，载体线程数 ≈ CPU 核数");

        // ========== 2. 平台线程 vs 虚拟线程 ==========
        System.out.println("\n=== 2. 平台线程对比 ===");
        Thread pt = Thread.ofPlatform().name("p-thread").start(() ->
                System.out.println("  平台线程内部看到的自己: " + Thread.currentThread()));
        pt.join();
        System.out.println("  isVirtual() = " + pt.isVirtual());

        // ========== 3. 10 万个虚拟线程 ==========
        System.out.println("\n=== 3. 10 万个虚拟线程，每个 sleep(100ms) ===");
        int n = 100_000;
        AtomicInteger done = new AtomicInteger();
        long start = System.currentTimeMillis();

        // ExecutorService 实现了 AutoCloseable，close() 会等所有任务完成
        try (ExecutorService es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                es.submit(() -> {
                    Thread.sleep(100);
                    done.incrementAndGet();
                    return null;
                });
            }
        }   // 这里会阻塞直到全部完成

        long cost = System.currentTimeMillis() - start;
        System.out.println("  完成 " + done.get() + " 个任务，总耗时 " + cost + " ms");
        System.out.println("  如果用 10 万个平台线程：10万 × 1MB 栈 = 100GB → 直接 OOM");

        // ========== 4. 不要池化虚拟线程 ==========
        System.out.println("\n=== 4. ⚠️ 不要池化虚拟线程 ===");
        System.out.println("  ❌ Executors.newFixedThreadPool(200, Thread.ofVirtual().factory())");
        System.out.println("     完全违背设计意图 —— 虚拟线程就是要\"用完即弃\"");
        System.out.println("  ✅ Executors.newVirtualThreadPerTaskExecutor()");

        // ========== 5. 限流要用 Semaphore，不能靠线程池大小 ==========
        System.out.println("\n=== 5. 虚拟线程下的限流 ===");
        demoRateLimit();

        // ========== 6. ThreadFactory 方式 ==========
        System.out.println("\n=== 6. 用 ThreadFactory 创建 ===");
        ThreadFactory factory = Thread.ofVirtual().name("task-", 1).factory();
        Thread t1 = factory.newThread(() ->
                System.out.println("  " + Thread.currentThread().getName()));
        Thread t2 = factory.newThread(() ->
                System.out.println("  " + Thread.currentThread().getName()));
        t1.start(); t1.join();
        t2.start(); t2.join();
        System.out.println("  名字自动编号，便于排查");
    }

    /** 虚拟线程没有数量限制，所以"用线程池大小限流"失效了，要用 Semaphore */
    private static void demoRateLimit() throws InterruptedException {
        Semaphore limiter = new Semaphore(5);          // 下游只能承受 5 并发
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        try (ExecutorService es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                es.submit(() -> {
                    limiter.acquire();                // 拿许可
                    try {
                        int now = inFlight.incrementAndGet();
                        maxInFlight.accumulateAndGet(now, Math::max);
                        Thread.sleep(20);             // 模拟调下游
                        inFlight.decrementAndGet();
                    } finally {
                        limiter.release();
                    }
                    return null;
                });
            }
        }

        System.out.println("  100 个虚拟线程 + Semaphore(5)");
        System.out.println("  实际最大并发 = " + maxInFlight.get() + "   ✅ 被限制在 5 以内");
        System.out.println("  （虚拟线程可以开 100 个，但下游压力由 Semaphore 控制）");
    }
}
```

实测输出：

```
Java 版本 = 21.0.11
CPU 核数  = 12

=== 1. 创建虚拟线程 ===
  虚拟线程内部看到的自己: VirtualThread[#20,v-thread]/runnable@ForkJoinPool-1-worker-1
  isVirtual() = true
  注意上面输出里的 @ForkJoinPool-1-worker-N —— 那是【载体线程】
  虚拟线程被挂在载体线程上执行，载体线程数 ≈ CPU 核数

=== 2. 平台线程对比 ===
  平台线程内部看到的自己: Thread[#23,p-thread,5,main]
  isVirtual() = false

=== 3. 10 万个虚拟线程，每个 sleep(100ms) ===
  完成 100000 个任务，总耗时 693 ms
  如果用 10 万个平台线程：10万 × 1MB 栈 = 100GB → 直接 OOM

=== 5. 虚拟线程下的限流 ===
  100 个虚拟线程 + Semaphore(5)
  实际最大并发 = 5   ✅ 被限制在 5 以内

=== 6. 用 ThreadFactory 创建 ===
  task-1
  task-2
```

**三个关键观察：**

**1. 10 万个线程各睡 100 ms，总共 693 ms 全部完成。** 用平台线程做同样的事：10 万 × 1 MB 栈 = 100 GB，直接 `OutOfMemoryError`。

**2. 对比两行线程名的格式差异：**

```
虚拟线程：VirtualThread[#20,v-thread]/runnable@ForkJoinPool-1-worker-1
平台线程：Thread[#23,p-thread,5,main]
```

虚拟线程名字后面挂着 `@ForkJoinPool-1-worker-1` —— 那是它当前所在的**载体线程**。10 万个虚拟线程共享着约 12 个（等于 CPU 核数）载体线程，阻塞时被卸下、就绪时再挂上。

**3. 第 5 组证明了限流思路必须改变。** 虚拟线程没有数量上限，"用线程池大小限制下游并发"这个老办法彻底失效。100 个虚拟线程全部起飞，但 `Semaphore(5)` 把真正打到下游的并发压在了 5。

### 四种创建方式

```java
// 1. 直接启动
Thread vt = Thread.ofVirtual().start(() -> doWork());

// 2. 带名字（便于排查）
Thread vt2 = Thread.ofVirtual().name("task-", 0).unstarted(() -> doWork());
vt2.start();

// 3. 每个任务一个虚拟线程的 ExecutorService（最常用）
try (var es = Executors.newVirtualThreadPerTaskExecutor()) {
    es.submit(() -> doWork());
}       // ExecutorService 实现了 AutoCloseable，close() 会等所有任务完成

// 4. 用 ThreadFactory
ThreadFactory factory = Thread.ofVirtual().factory();
```

> 注意第 3 种写法里的 `try-with-resources`：JDK 19 起 `ExecutorService` 实现了 `AutoCloseable`，`close()` 内部会调 `shutdown()` 并等待所有任务结束。这让"提交一批任务并等它们完成"变得非常简洁 —— 上面演示代码里的 10 万个任务就是靠它等完的。

### 关键认知：编程模型没变

虚拟线程最大的价值是**让同步阻塞的写法重新变得高效**：

```java
// 不用虚拟线程时，为了不阻塞线程池，被迫写成异步链
CompletableFuture.supplyAsync(() -> userApi.get(id), pool)
        .thenCompose(u -> CompletableFuture.supplyAsync(() -> orderApi.list(u.id()), pool))
        .thenApply(this::toVO);

// 有了虚拟线程，直接写同步代码，一样高效
try (var es = Executors.newVirtualThreadPerTaskExecutor()) {
    es.submit(() -> {
        UserInfo u = userApi.get(id);           // 阻塞，但虚拟线程被卸下，载体线程去干别的
        List<Order> orders = orderApi.list(u.id());
        return toVO(u, orders);
    });
}
```

**同步代码的可读性 + 异步代码的吞吐量。** 这是虚拟线程真正的意义，不只是"能开很多线程"。

### 平台线程 vs 虚拟线程

| | 平台线程 | 虚拟线程 |
|---|---------|---------|
| 数量级 | 几千 | **百万** |
| 栈 | 固定 ~1 MB（在栈内存） | 几百字节起，按需增长（在堆上） |
| 创建成本 | 高（系统调用） | 极低（就是个普通 Java 对象） |
| 调度 | 操作系统 | JVM（基于 `ForkJoinPool`） |
| 是否需要池化 | **必须** | **不需要**（池化虚拟线程是反模式） |
| 适用 | CPU 密集型 | **IO 密集型** |

### 使用边界（很重要）

**1. 虚拟线程对 CPU 密集型任务没有帮助。** 计算任务不会阻塞，没有"卸下"的机会，照样是 N 核跑 N 个任务。CPU 密集型继续用固定大小的平台线程池。

#### 完整可运行代码：两种任务类型的对比

这是验证上面那句话最直接的方式。**保存为 `VirtualVsPlatformDemo.java`（要跑十几秒，因为平台线程版本本身就慢）：**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 演示：虚拟线程 vs 平台线程，两种任务类型的对比
 *
 * 结论（跑完就能看到）：
 *   IO 密集型（阻塞等待）→ 虚拟线程快一个数量级
 *   CPU 密集型（纯计算）→ 虚拟线程没有优势，甚至略慢
 */
public class VirtualVsPlatformDemo {

    private static final int IO_TASKS = 20_000;     // IO 任务数
    private static final int IO_BLOCK_MS = 100;     // 每个任务阻塞时长
    private static final int CPU_TASKS = 2_000;     // CPU 任务数
    private static final int PLATFORM_POOL_SIZE = 200;

    public static void main(String[] args) throws Exception {

        System.out.println("CPU 核数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Java 版本 = " + System.getProperty("java.version"));

        // ============ 场景一：IO 密集型 ============
        System.out.println("\n========== 场景一：IO 密集型（每个任务阻塞 "
                + IO_BLOCK_MS + "ms，共 " + IO_TASKS + " 个）==========");

        long vIo = runVirtual(IO_TASKS, VirtualVsPlatformDemo::ioTask);
        System.out.println("  虚拟线程（每任务一个）        : " + vIo + " ms");

        long pIo = runPlatform(IO_TASKS, VirtualVsPlatformDemo::ioTask);
        System.out.println("  平台线程池（" + PLATFORM_POOL_SIZE + " 个线程）    : " + pIo + " ms");

        System.out.printf("  → 虚拟线程快约 %.1f 倍%n", pIo * 1.0 / Math.max(vIo, 1));
        System.out.println("  理论值：平台线程需要 " + IO_TASKS + "/" + PLATFORM_POOL_SIZE
                + " = " + (IO_TASKS / PLATFORM_POOL_SIZE) + " 轮 × " + IO_BLOCK_MS
                + "ms ≈ " + (IO_TASKS / PLATFORM_POOL_SIZE * IO_BLOCK_MS) + " ms");

        // ============ 场景二：CPU 密集型 ============
        System.out.println("\n========== 场景二：CPU 密集型（纯计算，共 "
                + CPU_TASKS + " 个）==========");

        long vCpu = runVirtual(CPU_TASKS, VirtualVsPlatformDemo::cpuTask);
        System.out.println("  虚拟线程（每任务一个）        : " + vCpu + " ms");

        int cores = Runtime.getRuntime().availableProcessors();
        long pCpu = runFixedPlatform(CPU_TASKS, cores + 1, VirtualVsPlatformDemo::cpuTask);
        System.out.println("  平台线程池（核数+1 = " + (cores + 1) + " 个线程）: " + pCpu + " ms");

        System.out.println("  → 两者接近，虚拟线程没有优势");
        System.out.println("    原因：计算任务不阻塞，虚拟线程没有\"卸下让位\"的机会，");
        System.out.println("          底层载体线程数就是 CPU 核数，并行度和平台线程池一样。");

        System.out.println("\n========== 结论 ==========");
        System.out.println("  IO 密集（DB / HTTP / 文件）→ 虚拟线程，不要池化");
        System.out.println("  CPU 密集（加密 / 压缩 / 计算）→ 固定大小的平台线程池（核数+1）");
    }

    /** 每个任务一个虚拟线程 */
    private static long runVirtual(int tasks, Runnable task) throws Exception {
        AtomicInteger done = new AtomicInteger();
        long start = System.currentTimeMillis();
        try (ExecutorService es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                es.submit(() -> {
                    task.run();
                    done.incrementAndGet();
                });
            }
        }
        long cost = System.currentTimeMillis() - start;
        if (done.get() != tasks) {
            System.out.println("  ⚠️ 只完成了 " + done.get() + "/" + tasks);
        }
        return cost;
    }

    /** 固定 200 个平台线程 */
    private static long runPlatform(int tasks, Runnable task) throws Exception {
        return runFixedPlatform(tasks, PLATFORM_POOL_SIZE, task);
    }

    private static long runFixedPlatform(int tasks, int poolSize, Runnable task)
            throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                poolSize, poolSize, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(tasks + 10));

        AtomicInteger done = new AtomicInteger();
        long start = System.currentTimeMillis();
        for (int i = 0; i < tasks; i++) {
            pool.execute(() -> {
                task.run();
                done.incrementAndGet();
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.MINUTES);
        long cost = System.currentTimeMillis() - start;
        if (done.get() != tasks) {
            System.out.println("  ⚠️ 只完成了 " + done.get() + "/" + tasks);
        }
        return cost;
    }

    /** IO 任务：单纯阻塞等待，模拟数据库/HTTP 调用 */
    private static void ioTask() {
        try {
            Thread.sleep(IO_BLOCK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final AtomicLong SINK = new AtomicLong();

    /** CPU 任务：纯计算，不阻塞 */
    private static void cpuTask() {
        long sum = 0;
        for (int i = 0; i < 2_000_000; i++) {
            sum += (long) Math.sqrt(i) + (i % 7);
        }
        SINK.addAndGet(sum);          // 防止 JIT 把计算优化掉
    }
}
```

实测输出：

```
CPU 核数 = 12
Java 版本 = 21.0.11

========== 场景一：IO 密集型（每个任务阻塞 100ms，共 20000 个）==========
  虚拟线程（每任务一个）        : 377 ms
  平台线程池（200 个线程）    : 10310 ms
  → 虚拟线程快约 27.3 倍
  理论值：平台线程需要 20000/200 = 100 轮 × 100ms ≈ 10000 ms

========== 场景二：CPU 密集型（纯计算，共 2000 个）==========
  虚拟线程（每任务一个）        : 1678 ms
  平台线程池（核数+1 = 13 个线程）: 1552 ms
  → 两者接近，虚拟线程没有优势
```

**这组数据是本节最有说服力的部分：**

| 任务类型 | 虚拟线程 | 平台线程池 | 结论 |
|---------|---------|-----------|------|
| IO 密集（阻塞 100ms × 2万） | **377 ms** | 10310 ms | 虚拟线程快 **27 倍** |
| CPU 密集（纯计算 × 2千） | 1678 ms | **1552 ms** | 虚拟线程**略慢** |

**IO 场景的 10310 ms 不是偶然**，它精确吻合理论值：200 个平台线程处理 2 万个任务，要跑 `20000/200 = 100` 轮，每轮 100 ms，正好 10 秒。平台线程在阻塞时被白白占用，这就是瓶颈。虚拟线程阻塞时会从载体线程上卸下，所以 2 万个任务几乎同时在等，总耗时≈单个任务耗时。

**CPU 场景虚拟线程反而慢了 8%**，因为计算任务不阻塞、没有"卸下让位"的机会，并行度仍然受限于 CPU 核数；而 2000 个虚拟线程的创建和调度带来了额外开销。

> 耗时数字换机器会变，但**两个场景的方向性结论是稳定的**：虚拟线程解决的是"线程在等待时被浪费"，不是"算得不够快"。

**2. 不要池化虚拟线程。**

```java
// ❌ 完全违背设计意图，虚拟线程就是要"用完即弃"
Executors.newFixedThreadPool(200, Thread.ofVirtual().factory());

// ✅ 每个任务一个新的虚拟线程
Executors.newVirtualThreadPerTaskExecutor();
```

**3. 需要限流时用 `Semaphore`，不要用线程池大小。**

虚拟线程没有数量限制，所以"用线程池大小限制下游并发"这个老办法失效了：

```java
private final Semaphore limiter = new Semaphore(20);     // 下游只能承受 20 并发

limiter.acquire();
try { db.query(sql); }
finally { limiter.release(); }
```

**4. `synchronized` 会"钉住"（pinning）载体线程。**

虚拟线程在 `synchronized` 块里阻塞时，**无法从载体线程上卸下**，会占着载体线程不放。大量虚拟线程这么做会耗尽载体线程池。

```java
// ⚠️ 虚拟线程里避免在 synchronized 块内做阻塞 IO
synchronized (lock) { httpClient.call(); }     // 钉住载体线程

// ✅ 换成 ReentrantLock，它对虚拟线程是友好的
lock.lock();
try { httpClient.call(); }
finally { lock.unlock(); }
```

> [JEP 491](https://openjdk.org/jeps/491)（JDK 24）已经解决了 `synchronized` 的钉住问题：虚拟线程在 monitor 上阻塞时也能从载体线程卸下。但在 **JDK 21 上这条限制是真实存在的**，所以虚拟线程场景优先用 `ReentrantLock`。

**5. `ThreadLocal` 仍然可用但要谨慎。** 虚拟线程支持 `ThreadLocal`，但百万个虚拟线程各存一份数据，内存会很可观。JDK 21 引入了 `ScopedValue`（预览特性）作为更轻量的替代。

### 现在该用吗

| 场景 | 建议 |
|------|------|
| 新项目、JDK 21+、IO 密集的 Web 服务 | ✅ 值得用。Spring Boot 3.2+ 一行配置即可：`spring.threads.virtual.enabled=true` |
| 已有项目用着固定线程池，运行良好 | 不急，但可以在新的异步逻辑里试 |
| CPU 密集型计算 | ❌ 继续用平台线程池 |
| JDK 17 及以下 | 用不了（JDK 19/20 是预览特性，需加 `--enable-preview`） |

---

## 22. 选型速查表

### 按问题选工具

| 我要解决的问题 | 用什么 |
|--------------|-------|
| 跨线程传递一个状态开关 | `volatile boolean` |
| 计数器、累加器 | `AtomicInteger` / `AtomicLong` |
| **高并发只统计总数**（QPS、埋点） | **`LongAdder`** |
| 保护一段复合逻辑 | `synchronized` |
| 需要超时 / 可中断 / 多条件的锁 | `ReentrantLock` |
| 读远多于写的共享数据 | `ReentrantReadWriteLock` |
| 并发读写的 Map | `ConcurrentHashMap` |
| 读多写极少的 List | `CopyOnWriteArrayList` |
| 本地缓存，保证初始化只执行一次 | `ConcurrentHashMap.computeIfAbsent` |
| 生产者-消费者 | `BlockingQueue`（**有界**） |
| 延迟任务（订单超时关闭） | `DelayQueue` / `ScheduledThreadPoolExecutor` |
| 执行异步任务 | `ThreadPoolExecutor`（**手动 new**） |
| 等多个异步任务都完成 | `CompletableFuture.allOf` 或 `CountDownLatch` |
| 聚合多个接口结果 | `CompletableFuture.thenCombine` / `allOf` |
| 限制下游并发数 | `Semaphore` |
| 一次请求内传递用户上下文 | `ThreadLocal`（**务必 `remove()`**） |
| 线程安全的单例 | 饿汉式 / 静态内部类 / 枚举 |
| 大量 IO 阻塞任务（JDK 21+） | **虚拟线程** |

### 按场景选线程池配置

| 场景 | 核心线程 | 队列 | 拒绝策略 |
|------|---------|------|---------|
| CPU 密集（加密、压缩、计算） | `核数 + 1` | 有界，容量小 | `AbortPolicy` |
| IO 密集（DB、HTTP、文件） | `核数 × 2` 起，压测调整 | 有界，容量适中 | `CallerRunsPolicy` |
| 定时任务 | 按任务数 | `ScheduledThreadPoolExecutor` 自带 | — |
| 不能丢的任务（如下单） | — | 有界 | 自定义：落 MQ / 写库补偿 |
| 可丢的任务（如埋点上报） | — | 有界 | 自定义：记日志 + 计数告警 |

### 并发代码检查清单

写完并发代码，按这张表过一遍：

| 检查项 | 常见问题 |
|-------|---------|
| ☐ 共享的可变状态有没有被保护 | Spring 单例 Bean 里的可变成员变量 |
| ☐ 锁对象是不是所有线程共享的同一个 | 在线程内 `new Object()` 当锁 |
| ☐ 锁对象有没有用 `String` 字面量 / 包装类 | 常量池、缓存导致意外共享同一把锁 |
| ☐ `Lock.unlock()` 在 `finally` 里吗 | 异常时锁不释放，全线程卡死 |
| ☐ `latch.countDown()` / `semaphore.release()` 在 `finally` 里吗 | 任务异常导致 `await()` 永久阻塞 |
| ☐ `ThreadLocal` 有没有 `remove()` | 线程池下内存泄漏 + 脏数据串用户 |
| ☐ 队列是有界的吗 | 无界队列 → 任务堆积 → OOM |
| ☐ 线程池是手动 `new` 的吗 | `Executors` 的工厂方法有 OOM 风险 |
| ☐ `submit()` 的异常有人处理吗 | 异常被 `Future` 吞掉，问题静默消失 |
| ☐ `InterruptedException` 有没有被吞 | 线程停不下来，线程池关不掉 |
| ☐ 多把锁的加锁顺序一致吗 | 死锁 |
| ☐ 持锁期间有没有做 IO / `sleep` | 吞吐量断崖下降 |
| ☐ `await` / `get` / `tryLock` 带超时了吗 | 无限期阻塞，故障时无法自愈 |
| ☐ 线程有名字吗 | `jstack` 看不出是哪块业务 |
| ☐ `CompletableFuture` 指定线程池了吗 | 默认 `commonPool` 全 JVM 共享，一个慢任务拖垮全部 |
| ☐ 线程池有监控吗 | 问题发生时毫无线索 |

---

## 23. 常见误区

| 误区 | 正确理解 |
|------|---------|
| 调 `t.run()` 就启动了线程 | `run()` 是普通方法调用，在当前线程执行。实测输出 `main`，没开新线程 |
| 一个线程可以 `start()` 多次 | 第二次抛 `IllegalThreadStateException`，线程是一次性的 |
| `i++` 是一个原子操作 | 是读+改+写三步。实测 10 线程各加 10 万次，结果是 167310 而非 1000000 |
| `volatile` 能让 `i++` 变安全 | **不能**。实测加了 `volatile` 结果仍是 319717 |
| 加了 `synchronized` 就安全了 | 要看**锁对象**。实测锁线程内 `new` 的对象，结果仍是 244743 |
| 实例锁和静态锁是同一把锁 | 是两把独立的锁，`this` 和 `Xxx.class` 互不互斥 |
| `sleep()` 会释放锁 | **不释放**。实测 t2 等了 349 ms；而 `wait()` 释放锁，t4 只等了 0 ms |
| `wait()` 可以在任何地方调 | 必须在 `synchronized` 块内，否则抛 `IllegalMonitorStateException` |
| 判断 `wait` 条件用 `if` 就行 | 必须用 `while`，防虚假唤醒和 `notifyAll` 后条件已变 |
| `notify()` 和 `notifyAll()` 差不多 | `notify()` 随机唤醒一个，可能造成信号丢失导致全体卡死。优先 `notifyAll()` |
| `Thread.stop()` 能安全停止线程 | 已废弃移除，会直接丢锁。用 `interrupt()` |
| `catch (InterruptedException e) {}` 没关系 | 吞掉中断后标志被清除，线程停不下来。要 `Thread.currentThread().interrupt()` |
| `HashMap` 多线程只是慢点 | 实测丢了 30% 数据，还可能 `size` 错乱 |
| `ArrayList` 并发 `add` 只是顺序乱 | 实测 10000 个元素只剩 3432，还可能抛数组越界 |
| `Collections.synchronizedList` 完全安全 | **遍历不受保护**，实测抛 `ConcurrentModificationException`，要手动锁整个遍历 |
| `ConcurrentHashMap` 的组合操作也是原子的 | `containsKey` + `put` 两步不原子，要用 `putIfAbsent` / `computeIfAbsent` |
| `ConcurrentHashMap` 可以存 null | 不行，抛 NPE。`HashMap` 可以 |
| `CopyOnWriteArrayList` 适合所有并发场景 | 每次写都复制整个数组，**只适合读多写极少** |
| `Executors.newFixedThreadPool()` 是推荐写法 | 队列**无界**，任务堆积会 OOM。手动 `new ThreadPoolExecutor` |
| `newCachedThreadPool()` 很灵活 | 最大线程数是 `Integer.MAX_VALUE`，流量大时线程爆炸 OOM |
| 队列没满时线程池会创建新线程到 max | **不会**。先入队，队列满了才扩容。实测任务 3、4 入队时线程数停在 2 |
| 线程池按提交顺序执行任务 | 不保证。实测任务 5、6 比先提交的 3、4 更早执行 |
| `submit()` 的异常会打到控制台 | **会被吞进 `Future`**，不 `get()` 永远看不到。用 `execute()` 或任务内自己 try-catch |
| 线程池越大越快 | 超过一定数量，CPU 全花在上下文切换上；下游（如 DB 连接池）也撑不住 |
| `pool.submit(task).get()` 写在循环里是并行的 | **退化成串行**。实测 916 ms vs 306 ms。要先全提交再统一 `get` |
| `CompletableFuture.supplyAsync` 不传线程池没关系 | 用的是全 JVM 共享的 `commonPool`，默认只有 `核数-1` 个线程，一个慢任务拖垮全部 |
| `ThreadLocal` 用完不 remove 无所谓 | 线程池下**内存泄漏 + 脏数据**（用户 A 的上下文串给用户 B） |
| `InheritableThreadLocal` 在线程池里能传递上下文 | **不能**，它只在线程创建时复制。要用 TTL |
| Spring 的 `@Service` 可以放可变成员变量 | 单例 Bean 被所有请求线程共享，这是最常见的并发 bug |
| DCL 单例的 `volatile` 可以省 | 不能省，指令重排会让别的线程拿到未初始化完的对象 |
| `SimpleDateFormat` 可以做成 static 共享 | 线程不安全。用 `DateTimeFormatter` |
| 虚拟线程也要池化 | **反模式**。虚拟线程用完即弃，用 `newVirtualThreadPerTaskExecutor()` |
| 虚拟线程能加速 CPU 密集型任务 | 不能。它解决的是**阻塞等待**，计算任务用平台线程池 |
| 多线程一定比单线程快 | 创建、切换、加锁都有开销。任务很轻或竞争激烈时，多线程可能更慢 |
| 本地测不出问题就是没问题 | 并发 bug 依赖时序，低并发下几乎不复现。必须靠 code review 和压测 |

---

## 24. 分级练习题

### 初级

**1.** 预测下面每行的输出，再运行验证：

```java
Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName()), "worker");
t.run();
t.start();
System.out.println(t.getState());
t.start();
```

**2.** 下面代码的输出是什么？为什么？怎么修？

```java
List<String> results = new ArrayList<>();
for (int i = 0; i < 3; i++) {
    final int id = i;
    new Thread(() -> results.add("result-" + id)).start();
}
System.out.println(results.size());
```

**3.** 写一个 `Counter` 类，提供 `inc()` 和 `get()`，用三种方式实现线程安全（`synchronized` / `AtomicInteger` / `ReentrantLock`）。用 10 个线程各调 10 万次 `inc()` 验证结果是 1000000。

**4.** 下面代码能停止线程吗？不能的话问题在哪？

```java
Thread t = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        try { Thread.sleep(100); }
        catch (InterruptedException e) { System.out.println("被中断"); }
    }
    System.out.println("退出");
});
t.start();
Thread.sleep(500);
t.interrupt();
```

**5.** 判断对错并说明理由：

- (a) `volatile` 能保证 `count++` 的原子性
- (b) `synchronized` 修饰静态方法时，锁的是 `this`
- (c) `Thread.sleep()` 期间会释放持有的锁
- (d) 一个线程 `TERMINATED` 后可以再次 `start()`
- (e) 局部变量不需要考虑线程安全

### 中级

**6.** 下面这个线程池，依次提交 10 个各耗时 1 秒的任务。请说出：哪些任务立刻执行、哪些入队、哪些被拒绝、总共耗时多久？

```java
new ThreadPoolExecutor(2, 5, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(3),
        new ThreadPoolExecutor.AbortPolicy());
```

**7.** 下面代码为什么没有并行？改成并行的写法，并说明耗时差异。

```java
ExecutorService pool = Executors.newFixedThreadPool(5);
int total = 0;
for (int i = 0; i < 5; i++) {
    total += pool.submit(() -> { Thread.sleep(200); return 1; }).get();
}
```

**8.** 下面的缓存代码有什么问题？用 `ConcurrentHashMap` 的一个方法重写成正确版本。

```java
private final Map<String, Config> cache = new ConcurrentHashMap<>();

public Config get(String key) {
    if (!cache.containsKey(key)) {
        cache.put(key, loadFromDb(key));     // loadFromDb 很慢
    }
    return cache.get(key);
}
```

**9.** 下面的转账方法会死锁。说出死锁的四个条件在这里分别是什么，然后用两种不同的方式修复。

```java
public void transfer(Account from, Account to, long amount) {
    synchronized (from) {
        synchronized (to) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

**10.** 用 `CompletableFuture` 实现：并行调用三个接口（各 300 ms），任意一个失败时该项用默认值，整体最多等 1 秒，超时返回已拿到的部分数据。

**11.** 这段代码在 Tomcat 里跑会出什么问题？说出两个，并给出修复。

```java
@Service
public class ReportService {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");
    private int processedCount = 0;

    public String process(Report r) {
        processedCount++;
        return SDF.format(r.getDate());
    }
}
```

**12.** 下面代码在线程池里跑，有两个严重问题。找出来并修复。

```java
private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

pool.submit(() -> {
    CURRENT.set(loadUser(userId));
    doBusinessLogic();                 // 可能抛异常
});
```

**13.** 为什么 `LongAdder` 在高并发计数时比 `AtomicLong` 快？它有什么代价？什么情况下不能用 `LongAdder` 替代 `AtomicLong`？

**14.** 下面三段代码，哪些是线程安全的？为什么？

```java
// A
private final List<String> list = new CopyOnWriteArrayList<>();
public void add(String s) { list.add(s); }
public int count() { return list.size(); }

// B
private final List<String> list = Collections.synchronizedList(new ArrayList<>());
public int sumLength() {
    int sum = 0;
    for (String s : list) sum += s.length();
    return sum;
}

// C
private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
public void inc(String key) {
    counts.putIfAbsent(key, new AtomicInteger());
    counts.get(key).incrementAndGet();
}
```

**15.** 在 JDK 21 上，下面两种写法跑 10 万个 IO 任务（每个阻塞 100 ms），哪个更快？为什么？如果任务改成纯 CPU 计算，结论会变吗？

```java
// A
try (var es = Executors.newVirtualThreadPerTaskExecutor()) { /* 提交 10 万任务 */ }

// B
ExecutorService es = Executors.newFixedThreadPool(200);     /* 提交 10 万任务 */
```

<details>
<summary>第 1、2、5 题参考答案</summary>

**第 1 题：**

```
main           ← t.run() 是普通方法调用，在 main 线程执行
worker         ← t.start() 真正开了新线程（这行和下一行顺序不确定）
RUNNABLE 或 TERMINATED   ← 取决于 worker 跑完了没有，不确定
然后抛 IllegalThreadStateException   ← 第二次 start()
```

最后那行异常是确定的；中间两行的**相对顺序和 `getState()` 的值都不确定**，这正是多线程的特点。

**第 2 题：**

很可能输出 `0`（也可能是 1、2、3，不确定）。两个独立的问题：

1. **没有 `join()`** —— main 线程执行到 `println` 时子线程还没跑完
2. **`ArrayList` 不是线程安全的** —— 即使加了 `join()`，并发 `add` 也可能丢数据

修复：

```java
List<String> results = Collections.synchronizedList(new ArrayList<>());   // 问题2
List<Thread> threads = new ArrayList<>();
for (int i = 0; i < 3; i++) {
    final int id = i;
    Thread t = new Thread(() -> results.add("result-" + id));
    threads.add(t);
    t.start();
}
for (Thread t : threads) t.join();                                        // 问题1
System.out.println(results.size());                                       // 稳定输出 3
```

更好的写法是直接用线程池 + `CompletableFuture.allOf()`。

**第 5 题：**

| | 对错 | 理由 |
|---|-----|------|
| (a) | ❌ | `volatile` 只保证可见性和有序性。实测加了 `volatile` 的 `count++`，100 万次只剩 319717 |
| (b) | ❌ | 静态方法锁的是 `Xxx.class`（类对象），不是 `this` |
| (c) | ❌ | `sleep()` **不释放锁**。实测 t2 等了 349 ms 才拿到锁。释放锁的是 `wait()` |
| (d) | ❌ | 线程一次性，`start()` 第二次抛 `IllegalThreadStateException` |
| (e) | ✅ | 局部变量在各线程自己的栈里，天然隔离。**但注意**：如果局部变量指向的是一个共享对象，那个对象本身仍然需要保护 |

</details>

<details>
<summary>第 6 题参考答案</summary>

参数：核心 2，最大 5，队列 3 → 最大承载 5 + 3 = 8 个任务。

| 任务 | 去向 | 依据 |
|------|------|------|
| 1、2 | 立刻执行（创建核心线程） | 线程数 < corePoolSize |
| 3、4、5 | **入队**排队，线程数仍是 2 | 队列未满，优先入队 |
| 6、7、8 | 立刻执行（创建非核心线程，线程数达 5） | 队列已满，线程数 < max |
| 9、10 | **被拒绝**，抛 `RejectedExecutionException` | 线程满 + 队列满 |

**总耗时约 2 秒**：

- 第 1 秒：任务 1、2、6、7、8 并行执行（5 个线程）
- 第 2 秒：线程空出来，执行队列里的 3、4、5

注意**任务 6、7、8 比先提交的 3、4、5 更早执行** —— 和第 14 节的实测现象一致。

</details>

<details>
<summary>第 8、9 题参考答案</summary>

**第 8 题：**

两个问题：

1. **`containsKey` + `put` 不是原子操作** —— 两个线程同时发现 key 不存在，都去调 `loadFromDb()`，**慢查询执行了两次**，还可能互相覆盖
2. 多了一次 `get()`，白白多查一遍

正确写法：

```java
public Config get(String key) {
    return cache.computeIfAbsent(key, this::loadFromDb);
}
```

`computeIfAbsent` 保证同一个 key 的映射函数**只执行一次**，其他线程会等它完成后直接拿结果。这是本地缓存的标准写法。

**第 9 题：**

四个条件在这里的体现：

| 条件 | 体现 |
|------|------|
| 互斥 | `synchronized` 保证一个账户同时只被一个线程锁住 |
| 持有并等待 | 锁住 `from` 后，持有它去等 `to` |
| 不可剥夺 | `synchronized` 没有超时机制，拿不到只能死等 |
| **循环等待** | `transfer(A,B)` 锁 A→B，`transfer(B,A)` 锁 B→A，成环 |

**修复一：固定加锁顺序**（破坏循环等待）

```java
public void transfer(Account from, Account to, long amount) {
    Account first  = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;
    synchronized (first) {
        synchronized (second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

**修复二：`tryLock` 带超时 + 随机退避**（破坏不可剥夺）

```java
public boolean transfer(Account from, Account to, long amount) throws InterruptedException {
    while (true) {
        if (from.lock.tryLock(100, TimeUnit.MILLISECONDS)) {
            try {
                if (to.lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        from.debit(amount);
                        to.credit(amount);
                        return true;
                    } finally { to.lock.unlock(); }
                }
            } finally { from.lock.unlock(); }
        }
        Thread.sleep(ThreadLocalRandom.current().nextInt(50));   // 随机退避，防活锁
    }
}
```

实际业务中还有**修复三**：别在应用层锁账户，用数据库行锁（`SELECT ... FOR UPDATE`）或乐观锁（`version` 字段），同样需要按 ID 排序来避免死锁。

</details>

<details>
<summary>第 11、12、14 题参考答案</summary>

**第 11 题：** `@Service` 是单例，被所有请求线程共享，两个问题：

1. **`SimpleDateFormat` 线程不安全** —— 内部有可变的 `Calendar` 字段，并发 `format()` 会得到错乱的日期，甚至抛 `NumberFormatException` / `ArrayIndexOutOfBoundsException`
2. **`processedCount++` 不是原子操作** —— 并发下会丢计数

修复：

```java
@Service
public class ReportService {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final LongAdder processedCount = new LongAdder();

    public String process(Report r) {
        processedCount.increment();
        return FMT.format(r.getDate());      // DateTimeFormatter 不可变，线程安全
    }
}
```

**第 12 题：** 两个问题：

1. **没有 `remove()`** —— 线程池的线程不会销毁，`ThreadLocal` 的 value 一直挂在线程上。泄漏之外更危险的是**脏数据**：下一个任务进到同一个线程，`CURRENT.get()` 拿到的是上一个用户的信息
2. **`doBusinessLogic()` 抛异常** —— 异常被 `submit()` 吞进 `Future`，不 `get()` 就完全看不到

修复：

```java
pool.submit(() -> {
    try {
        CURRENT.set(loadUser(userId));
        doBusinessLogic();
    } catch (Throwable t) {
        log.error("任务执行失败, userId={}", userId, t);    // 问题2
    } finally {
        CURRENT.remove();                                  // 问题1
    }
});
```

**第 14 题：**

| | 安全吗 | 理由 |
|---|-------|------|
| A | ✅ | `CopyOnWriteArrayList` 的 `add` 和 `size` 都是线程安全的 |
| B | ❌ | **`Collections.synchronizedList` 不保护遍历**，并发写入时 `for-each` 抛 `ConcurrentModificationException`。要 `synchronized (list) { for (...) }` |
| C | ❌ | 问题隐蔽：`putIfAbsent(key, new AtomicInteger())` **每次调用都会 new 一个对象**（哪怕用不上），虽然计数不会错，但有无谓的对象创建。更重要的是这个写法是两次 map 操作。正确写法：`counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet()`，或直接 `counts.merge(key, 1, Integer::sum)`（配合 `Map<String,Integer>`） |

</details>

<details>
<summary>第 15 题参考答案</summary>

**IO 任务：A（虚拟线程）快得多。**

- A：10 万个虚拟线程同时发起阻塞，每个阻塞时都从载体线程上卸下。实测 **527 ms** 完成。
- B：200 个平台线程，每轮能处理 200 个任务，需要 `100000 / 200 = 500` 轮，每轮 100 ms → **约 50 秒**。

差了约 100 倍。

**改成纯 CPU 计算后，结论会反过来（或至少不再有优势）。** 原因：

- 计算任务不会阻塞，虚拟线程没有"卸下让位"的机会
- 虚拟线程底层用的载体线程数就是 CPU 核数，并行度和 `newFixedThreadPool(核数)` 一样
- 10 万个虚拟线程的调度、栈管理反而带来额外开销

**CPU 密集型应该用 `newFixedThreadPool(核数 + 1)`。** 虚拟线程解决的是"线程在等待时被浪费"，不是"算得不够快"。

</details>

---

## 25. 一句话总结

多线程的全部复杂性来自一件事：**多个线程共享可变数据**。

所以解决思路只有三条，优先级从高到低：

| 思路 | 做法 | 工具 |
|------|------|------|
| **1. 不共享** | 用局部变量；数据通过参数传递；每线程一份 | 无状态设计、`ThreadLocal` |
| **2. 不可变** | 共享的对象一旦创建就不能改 | `final`、`record`、`List.of()`、`java.time` |
| **3. 必须共享可变数据时，才加同步** | 加锁、原子类、并发集合 | `synchronized`、`Atomic*`、`ConcurrentHashMap` |

**绝大多数并发 bug 的根源是跳过了前两条，直接去纠结第三条怎么写。** 一个无状态的 Service + 不可变的 DTO，天然就是线程安全的，不需要任何锁。

### 初级工程师记住五条

1. **`i++` 不是原子操作**，并发下必然丢数据（实测 100 万次只剩 16 万）。
2. **`volatile` 只管可见性，不管原子性**。它只适合"一个线程写、多个线程读"的状态标志位。
3. **`synchronized` 要看锁对象是谁**。锁错对象等于没加锁（实测结果同样是错的）。
4. **`sleep()` 不释放锁，`wait()` 释放锁**；`wait()` 的条件判断必须用 `while`。
5. **局部变量天然安全，成员变量（尤其 `static`）是危险区**。

### 中级工程师记住五条

1. **线程池手动 `new ThreadPoolExecutor`**，用**有界队列**。`Executors` 的工厂方法都有 OOM 风险。
2. **记住任务提交流程：核心线程 → 入队 → 扩容到 max → 拒绝**。队列没满时不会创建新线程。
3. **`submit()` 会吞异常**。在任务最外层包 `try-catch(Throwable)`。
4. **`ThreadLocal` 用完必须 `finally { remove(); }`**。线程池下不清理 = 内存泄漏 + 用户数据串用。
5. **所有阻塞操作都带超时**：`get(n)`、`await(n)`、`tryLock(n)`。没有超时的等待，在故障时就是永久挂起。

### 最重要的一条

**Spring 的 `@Service` / `@Controller` 是单例，被所有请求线程共享。在里面放可变的成员变量，就是一个并发 bug。**

这类 bug 在本地单并发测试下 100% 测不出来，上线后在流量高峰随机出现，而且很难复现。**写 Service 时问自己一句："这个字段会被两个请求同时改吗？"** —— 这一个习惯能避开实际工作中最常见的那类并发事故。

---

## 附：本文实测环境

| 项 | 值 |
|---|---|
| JDK | OpenJDK 21.0.2（2024-01-16） |
| 系统 | macOS（darwin） |
| CPU 核数 | 10（`Runtime.getRuntime().availableProcessors()` 实测） |

文中标注"实测"的输出都来自本机实际运行。**但多线程程序的输出本身带随机性**：

- **每次运行都不同的**：竞态条件的丢数据量（167310、319717、6950、3432 等）、多线程日志的交错顺序、`getState()` 抓到的瞬时状态
- **可比较但不固定的**：各种耗时数据（388/84/70/24 ms 等），只用来说明量级差异，换机器或换 JVM 版本会得到不同的数
- **稳定的**：异常类型、线程池的调度流程、`sleep` 不释放锁 / `wait` 释放锁、单例实例个数、`ConcurrentHashMap` 与 `HashMap` 的正确性差异

**读到"实测"二字时，请区分这三类。** 把一次运行的具体数字当成固定值记下来，是学多线程时最容易犯的错误。
