# 多线程演示代码

《Java 基础之多线程》第 14–21 节的全部可运行示例，共 **23 个文件**。
每个 `.java` 文件都是独立的、带 `main` 方法的完整程序，直接编译运行即可。

## 环境要求

- **JDK 21+**（第 21 节的虚拟线程需要；其余章节 JDK 17 也能跑）
- 验证环境：OpenJDK 21.0.11，macOS，12 核

```bash
java -version    # 确认是 21 或更高
```

## 怎么运行

每个目录独立编译。例如跑第 14 节的线程池流程演示：

```bash
cd s14_threadpool
javac -encoding UTF-8 *.java
java ThreadPoolFlowDemo
```

> `-encoding UTF-8` 必须加，否则中文注释在某些环境下会编译报错。

一次跑完某一节的所有程序：

```bash
cd s16_collections
javac -encoding UTF-8 *.java
for c in *.class; do java "${c%.class}"; done
```

## 目录与文件清单

| 目录 | 对应章节 | 文件 | 演示内容 |
|------|---------|------|---------|
| `s14_threadpool` | 14. 线程池 | `ThreadPoolFlowDemo` | 七参数 + 任务提交四步流程 |
| | | `RejectPolicyDemo` | 四种拒绝策略 + 自定义策略 |
| | | `OrderThreadPool` | 生产级线程池模板（无 main，被下面那个调用） |
| | | `OrderThreadPoolDemo` | 模板的使用：提交 → 监控 → 优雅关闭 |
| | | `ExceptionDemo` | `submit` 吞异常的四种情况对比 |
| | | `ShutdownDemo` | `shutdown()` vs `shutdownNow()` |
| `s15_future` | 15. Future | `FutureDemo` | 基本 API + `get()` 位置决定并行还是串行 |
| | | `CompletableFutureDemo` | 七组常用 API |
| | | `AggregateDemo` | 聚合三个接口（含降级和超时兜底） |
| `s16_collections` | 16. 并发集合 | `UnsafeCollectionDemo` | 六种集合实现的并发丢数据对比 |
| | | `ConcurrentHashMapDemo` | 组合操作不原子 + `computeIfAbsent` |
| | | `IterationSafetyDemo` | 遍历时被修改：CME vs 快照 |
| `s17_threadlocal` | 17. ThreadLocal | `ThreadLocalBasicDemo` | 基本隔离效果 |
| | | `ThreadLocalPollutionDemo` | **线程池脏数据 + 内存泄漏复现** |
| | | `InheritableThreadLocalDemo` | 线程池下 `InheritableThreadLocal` 失效 |
| `s18_tools` | 18. 并发工具类 | `CountDownLatchDemo` | 基本用法 + 异常导致永久阻塞的坑 |
| | | `CyclicBarrierDemo` | 多阶段屏障，可循环复用 |
| | | `SemaphoreDemo` | 限流，含不可重入的陷阱 |
| `s19_deadlock` | 19. 死锁 | `DeadlockDemo` | 复现死锁 + JMX 检测 |
| | | `DeadlockFixDemo` | 两种修复方案的压测对比 |
| `s20_singleton` | 20. 单例 | `SingletonDemo` | 四种写法并发验证 + 反射攻击 |
| `s21_virtual` | 21. 虚拟线程 | `VirtualThreadDemo` | 10 万虚拟线程 + Semaphore 限流 |
| | | `VirtualVsPlatformDemo` | IO 密集 vs CPU 密集的性能对比 |

## 几个注意事项

**1. 多线程程序的输出带随机性。** 凡是涉及竞态的演示（`UnsafeCollectionDemo` 丢了多少个、
`SemaphoreDemo` 任务编号的顺序、各种耗时数字），**每次运行结果都不一样**。
文档里给的数字只用于说明量级，不要当成固定值。

稳定的是：异常类型、线程池调度流程、单例实例个数、正确/错误实现之间的差异方向。

**2. 两个程序跑得比较久：**

- `VirtualVsPlatformDemo` —— 约 15 秒（平台线程版本本身就慢，这正是要演示的）
- `DeadlockFixDemo` —— 约 1 秒，但如果你改坏了代码触发死锁，会等满 15 秒超时

**3. `DeadlockDemo` 里的线程是守护线程。** 这样 JVM 最后能正常退出。
想体验"进程彻底卡死"的真实效果，把两行 `setDaemon(true)` 注释掉再跑 —— 然后用 Ctrl+C 退出。

**4. `ThreadLocalPollutionDemo` 会分配 20MB 内存。** 默认堆足够，无需额外参数。

**5. `OrderThreadPool.java` 没有 main 方法。** 它是被 `OrderThreadPoolDemo` 使用的线程池类，
两个文件必须一起编译：

```bash
javac -encoding UTF-8 OrderThreadPool.java OrderThreadPoolDemo.java
java OrderThreadPoolDemo
```

## 最值得先跑的三个

如果时间有限，这三个的"看到现象才会信"的价值最高：

1. **`ThreadLocalPollutionDemo`** —— 请求 2 一行 `set` 都没写，却读到了请求 1 的用户身份。
   这是数据越权，属于安全事故。
2. **`ThreadPoolFlowDemo`** —— 任务 3、4 入队时线程数停在 2 不动，
   亲眼看到"队列没满就不创建新线程"。
3. **`VirtualVsPlatformDemo`** —— 同样 2 万个 IO 任务，377 ms vs 10310 ms。
