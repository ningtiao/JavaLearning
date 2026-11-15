# Java 基础之接口

> 本文按**初级 → 中级 → 高级**三段编排，每段都可独立阅读：
>
> - **第一部分（初级）**：接口是什么、语法规则、和抽象类怎么选
> - **第二部分（中级）**：**接口的八大应用场景** —— 面向接口编程、策略模式、回调、函数式接口、SPI 等
> - **第三部分（高级）**：`default` 方法的冲突解决规则、动态代理、密封接口、接口设计的工程原则
>
> 本文所有代码输出均在 **JDK 21** 上实测，包括编译错误信息。

---

## 目录

**第一部分：基础（初级工程师）**

1. [接口是什么：契约与能力](#1-接口是什么契约与能力)
2. [语法与成员规则](#2-语法与成员规则)
3. [实现接口的规则](#3-实现接口的规则)
4. [接口 vs 抽象类](#4-接口-vs-抽象类)
5. [接口之间的多继承](#5-接口之间的多继承)

**第二部分：接口的应用（中级工程师）**

6. [应用总览](#6-应用总览)
7. [应用一：面向接口编程与依赖倒置](#7-应用一面向接口编程与依赖倒置)
8. [应用二：策略模式，消灭 if-else](#8-应用二策略模式消灭-if-else)
9. [应用三：回调机制](#9-应用三回调机制)
10. [应用四：函数式接口与 Lambda](#10-应用四函数式接口与-lambda)
11. [应用五：Comparable 与 Comparator](#11-应用五comparable-与-comparator)
12. [应用六：标记接口](#12-应用六标记接口)
13. [应用七：适配器与多重身份](#13-应用七适配器与多重身份)
14. [应用八：常量接口（反模式）](#14-应用八常量接口反模式)
15. [JDK 中的接口设计范例](#15-jdk-中的接口设计范例)

**第三部分：进阶（高级工程师）**

16. [default 方法：动机与四条冲突规则](#16-default-方法动机与四条冲突规则)
17. [static 与 private 接口方法](#17-static-与-private-接口方法)
18. [SPI 与 ServiceLoader](#18-spi-与-serviceloader)
19. [为什么 JDK 动态代理只能代理接口](#19-为什么-jdk-动态代理只能代理接口)
20. [密封接口 sealed interface](#20-密封接口-sealed-interface)
21. [接口设计的工程原则](#21-接口设计的工程原则)

**收尾**

22. [常见误区](#22-常见误区)
23. [分级练习题](#23-分级练习题)

---

# 第一部分：基础（初级工程师）

## 1. 接口是什么：契约与能力

一句话：**接口定义"能做什么"，不管"怎么做"。**

### 从一个真实问题切入

你要写一个订单服务，下单成功后发通知。通知方式可能是短信、邮件、App 推送，而且随时会增加新方式。

**不用接口的写法：**

```java
class OrderService {
    void createOrder(Order order) {
        // ... 保存订单 ...
        
        // 发通知：写死了具体实现
        SmsSender sms = new SmsSender();
        sms.sendSms(order.getPhone(), "下单成功");
    }
}
```

问题很明显：

- 要改成发邮件，必须**修改 `OrderService` 的源码**
- 要同时发短信和邮件，得继续往里面加代码
- 想单元测试 `createOrder`，会真的发出一条短信

**用接口的写法：**

```java
// 契约：我只要求"能发通知"，不管你怎么发
interface Notifier {
    void notify(String target, String message);
}

class OrderService {
    private final Notifier notifier;      // 依赖接口，不依赖具体实现
    
    OrderService(Notifier notifier) {     // 从外部传入
        this.notifier = notifier;
    }
    
    void createOrder(Order order) {
        // ... 保存订单 ...
        notifier.notify(order.getPhone(), "下单成功");
    }
}
```

现在：

- 新增通知方式 = 新写一个 `implements Notifier` 的类，`OrderService` **一行都不用改**
- 测试时传一个什么都不做的假实现，不会真发短信
- 同时发多种？写一个内部持有多个 `Notifier` 的组合实现就行

**这就是接口的核心价值：把"变化的部分"隔离在接口之后。**

### 接口的语义：can-do

| 类型 | 语义 | 例子 |
|------|------|------|
| 类继承 | **is-a**（是什么） | `Dog` **是一种** `Animal` |
| 接口 | **can-do**（能做什么） | `Dog` **能够** `Serializable`、**能够** `Comparable` |

一个对象只能"是"一种东西（单继承），但可以"能做"很多事（多实现）。这是接口和抽象类最本质的分工。

---

## 2. 语法与成员规则

```java
public interface Notifier {
    // 1. 常量
    int MAX_RETRY = 3;
    
    // 2. 抽象方法（最常用）
    void notify(String target, String message);
    
    // 3. default 方法（Java 8+）：有默认实现
    default void notifyAll(List<String> targets, String msg) {
        for (String t : targets) notify(t, msg);
    }
    
    // 4. static 方法（Java 8+）：工具方法
    static Notifier noop() {
        return (target, message) -> { };
    }
    
    // 5. private 方法（Java 9+）：供 default 方法复用的内部逻辑
    private String format(String msg) {
        return "[通知] " + msg;
    }
}
```

### 隐含修饰符（实测确认）

接口成员有一套**隐含修饰符**，写不写效果一样。用反射读出来看看：

```java
interface Konst {
    int MAX = 100;     // 没写任何修饰符
    void doIt();       // 没写任何修饰符
}
```

实测结果：

```
接口字段修饰符    : public static final
接口抽象方法修饰符: public abstract
```

所以这两种写法完全等价：

```java
int MAX = 100;
public static final int MAX = 100;   // 完全一样，啰嗦
```

**实践约定：不要写这些冗余修饰符。** IDE 会把它们标成灰色提示你删掉。

### 字段是常量，必须初始化，不能修改

```java
interface X3 {
    int count = 0;   // 隐含 final
}
class UseX3 implements X3 {
    public void m() {
        count = 5;   // 编译错误
    }
}
```

实测编译错误：

```
错误: 无法为 static final 变量 count 分配值
```

接口**不能有实例字段**，也就是**不能保存对象状态**。这是接口和抽象类最硬的差别。

### 接口不能有构造方法

接口不能被实例化，也没有实例字段要初始化，所以构造方法没有意义，语法上直接禁止。

### default 方法不能重写 Object 的方法

```java
interface X2 {
    default String toString() { return "不允许"; }
}
```

实测编译错误：

```
错误: 接口 X2 中的默认方法toString覆盖了 java.lang.Object 的成员
```

`toString()`、`equals()`、`hashCode()` 都不行。原因是每个类都已经从 `Object` 继承了这些方法，按"类优先"规则（见第 16 节），接口的版本永远不会被调用，允许这么写只会造成误解。

### 各版本能力一览

| 成员类型 | 引入版本 | 用途 |
|---------|---------|------|
| 抽象方法 | Java 1.0 | 定义契约 |
| 常量 | Java 1.0 | 极少用，见第 14 节 |
| `default` 方法 | **Java 8** | 为已有接口添加方法而不破坏实现类 |
| `static` 方法 | **Java 8** | 接口相关的工具方法 |
| `private` 方法 | **Java 9** | 抽取 `default` 方法间的公共逻辑 |
| `sealed` 接口 | **Java 17** | 限定实现类范围，配合模式匹配 |

---

## 3. 实现接口的规则

### 基本规则

```java
class SmsNotifier implements Notifier {
    @Override
    public void notify(String target, String message) {
        System.out.println("短信发往 " + target + ": " + message);
    }
}
```

五条要记住：

1. 用 `implements` 关键字，可以同时实现**多个**接口（逗号分隔）。
2. 必须实现**所有**抽象方法，否则这个类得声明为 `abstract`。
3. 实现方法的访问权限**必须是 `public`** —— 因为接口方法隐含 `public`，重写不能缩小权限。
4. `default` 方法可以不管（直接继承），也可以重写。
5. 一个类可以**同时 `extends` 一个类和 `implements` 多个接口**，顺序是 `extends` 在前。

```java
class MyList extends AbstractList<String> implements Serializable, Cloneable {
    // extends 在前，implements 在后
}
```

### 常见编译错误：权限缩小

```java
class Bad implements Notifier {
    @Override
    void notify(String t, String m) { }   // 错误：漏了 public
}
```

报错是"正在尝试分配更低的访问权限"。初学者很容易忘记写 `public`，因为抽象类里可以用 `protected`。

### 实现多个接口时的方法重名

如果两个接口有**签名完全相同的抽象方法**，实现一次就同时满足两者：

```java
interface A { void run(); }
interface B { void run(); }

class C implements A, B {
    @Override public void run() { }   // 一个实现，满足两个接口
}
```

但如果是**两个 `default` 方法**重名，就会冲突报错 —— 这是第 16 节的内容。

---

## 4. 接口 vs 抽象类

这是初级面试的高频题。核心差异：

| 对比项 | 接口 `interface` | 抽象类 `abstract class` |
|--------|-----------------|------------------------|
| **数量限制** | 可实现**多个** | 只能继承**一个** |
| **实例字段** | ❌ 只能有 `public static final` 常量 | ✅ 任意字段，**可保存状态** |
| **构造方法** | ❌ 没有 | ✅ 有，供子类 `super()` 调用 |
| **方法实现** | Java 8+ 可用 `default`/`static` | 抽象方法与普通方法任意混搭 |
| **访问修饰符** | 方法隐含 `public`（Java 9+ 可 `private`） | `public`/`protected`/默认/`private` 都行 |
| **`final` 方法** | ❌ 不能，`default` 方法无法 `final` | ✅ 可以，用于锁定模板流程 |
| **语义** | **can-do** 能做什么 | **is-a** 是什么 |
| **演进成本** | 加抽象方法会**破坏**所有实现类（除非用 `default`） | 加普通方法不影响子类 |

### 三个关键区别的实际影响

**区别一：状态。** 抽象类能存字段，所以能做"骨架实现"；接口不能，所以只能定契约。

```java
// 抽象类：可以持有共享状态
abstract class AbstractCache {
    protected final Map<String, Object> store = new HashMap<>();   // 状态
    public Object get(String k) { return store.get(k); }           // 复用实现
    public abstract void evict();
}

// 接口：无状态，只有契约
interface Cache {
    Object get(String k);
    void evict();
}
```

**区别二：数量。** 需要多重身份时，接口是唯一选择。

```java
// 一个类可以有很多"能力"，但只能"是"一种东西
class Order extends BaseEntity implements Serializable, Comparable<Order>, Cloneable {
}
```

实测 `ArrayList` 实现的接口：

```
[interface java.util.List, interface java.util.RandomAccess,
 interface java.lang.Cloneable, interface java.io.Serializable]
```

四个接口 + 继承 `AbstractList` —— 这正是典型用法。

**区别三：锁定流程。** 抽象类能用 `final` 方法锁住模板方法的执行顺序，接口做不到（`default` 方法必须允许被重写）。所以**模板方法模式只能用抽象类**。

### 怎么选

问三个问题：

1. **子类之间要共享代码或字段吗？** 要 → 抽象类
2. **这是"是什么"还是"能做什么"？** 是什么 → 抽象类；能做什么 → 接口
3. **实现类需要多重身份吗？** 需要 → 只能用接口

**实际项目的标准答案是两个一起用：接口定契约，抽象类提供骨架实现。** 详见第 15 节。

---

## 5. 接口之间的多继承

接口和接口之间用 `extends`，而且**可以继承多个**（类不行）：

```java
interface Readable  { String read(); }
interface Writable  { void write(String s); }

// 接口可以多继承
interface ReadWritable extends Readable, Writable {
    default void copyFrom(Readable src) {
        write(src.read());
    }
}

// 实现子接口 = 必须实现所有继承来的方法
class File implements ReadWritable {
    private String content = "";
    @Override public String read() { return content; }
    @Override public void write(String s) { this.content = s; }
}
```

注意关键字的区别：

| 关系 | 关键字 | 数量 |
|------|-------|------|
| 类 → 类 | `extends` | 1 个 |
| 类 → 接口 | `implements` | 多个 |
| **接口 → 接口** | **`extends`** | **多个** |

接口能多继承，是因为它**没有状态**，不存在"该继承哪个字段"的歧义。类不能多继承，核心就是为了避免这种歧义（经典的"菱形问题"）。

> Java 8 引入 `default` 方法后，接口有了"实现"，菱形问题部分回归了 —— 所以才需要第 16 节那套冲突解决规则。

---

# 第二部分：接口的应用（中级工程师）

## 6. 应用总览

这是本文的核心部分。接口在实际工程里的用法可以归为八类：

| # | 应用 | 一句话概括 | 典型场景 |
|---|------|-----------|---------|
| 1 | **面向接口编程 / 依赖倒置** | 上层依赖抽象，不依赖实现 | Spring 的 `@Autowired`、分层架构 |
| 2 | **策略模式** | 用多态消灭 `if-else` | 支付渠道、优惠券计算、导出格式 |
| 3 | **回调机制** | 把"之后做什么"交给调用方 | 事件监听、异步通知、钩子 |
| 4 | **函数式接口** | 让行为能当参数传递 | Lambda、Stream、`Comparator` |
| 5 | **比较与排序** | `Comparable` / `Comparator` | 集合排序、`TreeMap` |
| 6 | **标记接口** | 不定义方法，只做类型标记 | `Serializable`、`Cloneable`、`RandomAccess` |
| 7 | **适配器与多重身份** | 一个类同时具备多种能力 | 适配老接口、`ArrayList` 的多接口 |
| 8 | **常量接口** | ⚠️ **反模式**，不要用 | 用 `enum` 或工具类替代 |

另外还有三个偏框架层面的用法，放在第三部分讲：**SPI 插件化**、**动态代理（AOP 基础）**、**密封接口 + 模式匹配**。

---

## 7. 应用一：面向接口编程与依赖倒置

这是接口最重要、也最容易被初学者忽略的应用。

### 问题：高层模块被低层实现绑死

```java
// ❌ 高层的 OrderService 直接依赖低层的 MySqlOrderDao
class OrderService {
    private MySqlOrderDao dao = new MySqlOrderDao();   // 硬编码依赖
    
    void save(Order o) { dao.insert(o); }
}
```

依赖方向是 `OrderService → MySqlOrderDao`，业务逻辑被数据库实现绑死。换成 MongoDB，业务代码就要改。

### 解法：依赖倒置（DIP）

引入接口，让**两边都依赖接口**：

```java
// 接口（归属于高层，定义高层需要什么）
interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(long id);
}

// 高层：只认接口
class OrderService {
    private final OrderRepository repo;
    
    OrderService(OrderRepository repo) {    // 构造方法注入
        this.repo = repo;
    }
    
    void createOrder(Order o) {
        repo.save(o);
    }
}

// 低层实现 1
class MySqlOrderRepository implements OrderRepository {
    public void save(Order o) { /* JDBC */ }
    public Optional<Order> findById(long id) { /* ... */ return Optional.empty(); }
}

// 低层实现 2：换数据库只需加一个类
class MongoOrderRepository implements OrderRepository {
    public void save(Order o) { /* MongoDB */ }
    public Optional<Order> findById(long id) { return Optional.empty(); }
}

// 低层实现 3：测试专用，不碰真实数据库
class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> data = new HashMap<>();
    public void save(Order o) { data.put(o.getId(), o); }
    public Optional<Order> findById(long id) { return Optional.ofNullable(data.get(id)); }
}
```

依赖方向变成了：

```
OrderService ──→ OrderRepository ←── MySqlOrderRepository
  （高层）         （接口/抽象）          （低层实现）
```

低层的依赖箭头被"倒置"了 —— 这就是**依赖倒置原则**的名字来源。

### 带来的三个实际好处

**1. 可替换。** 换实现不动业务代码。

**2. 可测试。** 这是日常开发中感受最直接的好处：

```java
// 单元测试：注入内存实现，不需要启动数据库
OrderService service = new OrderService(new InMemoryOrderRepository());
service.createOrder(new Order(1L));
// 测试跑在毫秒级，不依赖任何外部环境
```

如果 `OrderService` 里写死了 `new MySqlOrderDao()`，这个测试根本没法写。**"代码难测试"几乎总是"依赖写死了"的症状。**

**3. 可并行开发。** 接口定好后，写业务逻辑的人和写数据访问的人可以同时开工。

### 和 Spring 的关系

Spring 的依赖注入就是这套思想的框架化：

```java
@Service
public class OrderService {
    private final OrderRepository repo;
    
    public OrderService(OrderRepository repo) {   // Spring 自动注入实现类
        this.repo = repo;
    }
}
```

你声明"我需要一个 `OrderRepository`"，Spring 负责找到实现类并塞进来。**你写的代码里不该出现具体实现类的名字** —— 这是面向接口编程的实践标准。

---

## 8. 应用二：策略模式，消灭 if-else

### 问题：不断膨胀的 if-else

```java
// ❌ 每增加一个支付渠道，就要回来改这个方法
class PaymentService {
    public String pay(String channel, long amount) {
        if ("ALIPAY".equals(channel)) {
            // 50 行支付宝逻辑
            return "支付宝支付成功";
        } else if ("WECHAT".equals(channel)) {
            // 50 行微信逻辑
            return "微信支付成功";
        } else if ("UNIONPAY".equals(channel)) {
            // 50 行银联逻辑
            return "银联支付成功";
        }
        throw new IllegalArgumentException("不支持的渠道");
    }
}
```

这段代码的三个问题：

- **违反开闭原则**：加渠道必须改已有代码，有改错的风险
- **方法越来越长**：三个渠道就 150 行，十个渠道没法维护
- **测试成本高**：改一个渠道，整个方法的测试都要重跑

### 解法：接口 + 多态 + 注册表

```java
// 1. 策略接口
interface PayStrategy {
    String channelCode();                      // 我负责哪个渠道
    PayResult pay(long amountInCents);         // 具体怎么支付
}

// 2. 各个策略独立成类，互不影响
class AliPayStrategy implements PayStrategy {
    public String channelCode() { return "ALIPAY"; }
    public PayResult pay(long cents) {
        // 只有支付宝的逻辑，独立测试
        return PayResult.ok("支付宝扣款 " + cents / 100.0 + " 元");
    }
}

class WeChatPayStrategy implements PayStrategy {
    public String channelCode() { return "WECHAT"; }
    public PayResult pay(long cents) {
        return PayResult.ok("微信扣款 " + cents / 100.0 + " 元");
    }
}

// 3. 上下文：用 Map 路由，没有一个 if-else
class PaymentService {
    private final Map<String, PayStrategy> registry = new HashMap<>();
    
    public PaymentService(List<PayStrategy> strategies) {
        for (PayStrategy s : strategies) {
            registry.put(s.channelCode(), s);
        }
    }
    
    public PayResult pay(String channel, long cents) {
        PayStrategy s = registry.get(channel);
        if (s == null) {
            throw new IllegalArgumentException("不支持的渠道: " + channel);
        }
        return s.pay(cents);
    }
}
```

**新增银联支付**：写一个 `UnionPayStrategy implements PayStrategy`，注册进去，`PaymentService` 一行不改。

### 配合 Spring 更省事

Spring 能自动把某个接口的所有实现收集成 `List` 或 `Map` 注入：

```java
@Service
public class PaymentService {
    private final Map<String, PayStrategy> registry;
    
    // Spring 自动注入所有 PayStrategy 实现，key 是 bean 名称
    public PaymentService(List<PayStrategy> strategies) {
        this.registry = strategies.stream()
                .collect(Collectors.toMap(PayStrategy::channelCode, s -> s));
    }
}
```

新增策略只需加 `@Component`，连注册代码都不用写。

### 什么时候该用策略模式

| 信号 | 说明 |
|------|------|
| `if-else` / `switch` 分支超过 3 个且**还会增加** | 典型信号 |
| 每个分支的逻辑都比较重（超过十几行） | 适合拆成独立类 |
| 分支之间逻辑完全独立 | 拆分后互不影响 |
| 同样的分支判断在多处重复出现 | 最强信号，说明抽象缺失 |

**反过来，不该滥用**：如果只有两个分支、每个就一两行、而且不会再增加，`if-else` 更清晰。为两行代码建三个类是过度设计。

---

## 9. 应用三：回调机制

**回调的本质：把"之后要做什么"作为参数传进去，由被调用方在合适的时机执行。**

### 场景：异步任务完成后通知调用方

```java
// 回调接口
interface UploadCallback {
    void onSuccess(String fileUrl);
    void onFailure(Exception e);
    
    // default 方法让实现类可以只关心自己在意的事件
    default void onProgress(int percent) { }
}

class FileUploader {
    public void upload(byte[] data, UploadCallback callback) {
        new Thread(() -> {
            try {
                for (int p = 0; p <= 100; p += 50) {
                    callback.onProgress(p);        // 过程回调
                    Thread.sleep(10);
                }
                callback.onSuccess("https://cdn.example.com/a.jpg");  // 成功回调
            } catch (Exception e) {
                callback.onFailure(e);             // 失败回调
            }
        }).start();
    }
}
```

调用方决定"之后做什么"：

```java
uploader.upload(bytes, new UploadCallback() {
    @Override public void onSuccess(String url) {
        System.out.println("上传完成，地址：" + url);
    }
    @Override public void onFailure(Exception e) {
        System.out.println("上传失败：" + e.getMessage());
    }
    @Override public void onProgress(int p) {
        System.out.println("进度 " + p + "%");
    }
});
```

**控制反转在这里体现得很清楚**：`FileUploader` 不知道上传成功后该干什么（存数据库？发通知？刷新 UI？），它只负责在正确的时机调用回调。

### 回调 vs 策略：容易混淆的区别

两者语法上都是"传一个接口实现进去"，区别在**意图和调用时机**：

| | 策略模式 | 回调 |
|---|---------|------|
| **意图** | 替换算法的**实现方式** | 在特定**时机**通知调用方 |
| **调用时机** | 方法执行过程中，作为主逻辑 | 事件发生时（成功/失败/进度） |
| **典型命名** | `XxxStrategy`、`XxxHandler` | `XxxCallback`、`XxxListener`、`onXxx` |

### 现代写法：用函数式接口替代

单方法回调现在通常不自己定义接口，直接用 JDK 的函数式接口：

```java
// 老写法：自定义接口 + 匿名内部类
uploader.upload(bytes, new UploadCallback() { ... });

// 新写法：用 Consumer，Lambda 一行
public void upload(byte[] data, Consumer<String> onSuccess, Consumer<Exception> onError) { }

uploader.upload(bytes,
    url -> System.out.println("完成: " + url),
    err -> System.out.println("失败: " + err));
```

JDK 的 `CompletableFuture` 就是这套思路的成熟形态：

```java
CompletableFuture
    .supplyAsync(() -> upload(bytes))       // 异步执行
    .thenAccept(url -> save(url))           // 成功回调
    .exceptionally(e -> { log(e); return null; });   // 失败回调
```

---

## 10. 应用四：函数式接口与 Lambda

### 什么是函数式接口

**只有一个抽象方法**的接口，叫函数式接口。它可以用 Lambda 表达式直接赋值。

```java
@FunctionalInterface
interface Calculator {
    int calc(int a, int b);                  // 唯一的抽象方法
    
    default String describe() {              // default 方法不影响
        return "我是函数式接口，但可以有 default 方法";
    }
    static Calculator plus() {               // static 方法也不影响
        return Integer::sum;
    }
}
```

实测：

```java
Calculator mul = (a, b) -> a * b;
System.out.println(mul.calc(6, 7));      // 42
System.out.println(mul.describe());      // 我是函数式接口，但可以有 default 方法
```

`@FunctionalInterface` 注解**不是必须的**，但建议加上 —— 它让编译器帮你检查"是否真的只有一个抽象方法"，防止别人后来误加了第二个抽象方法把所有 Lambda 调用点都搞崩。

### JDK 四大核心函数式接口（实测输出）

| 接口 | 抽象方法 | 语义 | 实测示例 |
|------|---------|------|---------|
| `Function<T,R>` | `R apply(T)` | 一进一出，**转换** | `x -> x * x` 作用于 7 → `49` |
| `Predicate<T>` | `boolean test(T)` | 一进一布尔，**判断** | `s -> !s.isBlank()` 作用于 `""` → `false` |
| `Consumer<T>` | `void accept(T)` | 只进不出，**消费** | `s -> print(s)` → 打印 `hello` |
| `Supplier<T>` | `T get()` | 只出不进，**生产** | `() -> "来自Supplier"` → `来自Supplier` |

扩展变体：

```java
BiFunction<Integer,Integer,Integer> add = Integer::sum;   // 两个参数
System.out.println(add.apply(3, 4));          // 7

UnaryOperator<String> upper = String::toUpperCase;        // 进出同类型
BinaryOperator<Integer> max = Integer::max;               // 两个同类型参数

// 避免装箱的特化版本（性能敏感时用）
IntPredicate even = n -> n % 2 == 0;
IntUnaryOperator twice = n -> n * 2;
```

### 组合：接口的 default 方法让函数可以拼接

```java
Function<Integer, Integer> sq = x -> x * x;
System.out.println(sq.andThen(x -> x + 1).apply(5));   // 26 （先平方=25，再+1）
System.out.println(sq.compose((Integer x) -> x + 1).apply(5));  // 36 （先+1=6，再平方）
```

实测 `sq.andThen(x -> x + 1).apply(5)` 输出 **26**。

`Predicate` 的组合更常用：

```java
Predicate<String> notNull  = s -> s != null;
Predicate<String> notEmpty = s -> !s.isEmpty();
Predicate<String> valid = notNull.and(notEmpty);     // 与
Predicate<String> invalid = valid.negate();          // 非
```

**注意这些 `and`/`or`/`negate`/`andThen` 全都是接口的 `default` 方法** —— 没有 Java 8 的 `default` 机制，就不可能有这套函数组合 API。这是 `default` 方法最有价值的应用。

### 四种 Lambda 写法与方法引用

```java
// 1. 匿名内部类（Java 8 之前）
Comparator<String> c1 = new Comparator<String>() {
    @Override public int compare(String a, String b) { return a.length() - b.length(); }
};

// 2. Lambda 完整写法
Comparator<String> c2 = (String a, String b) -> { return a.length() - b.length(); };

// 3. Lambda 简化（类型推断 + 省略 return）
Comparator<String> c3 = (a, b) -> a.length() - b.length();

// 4. 方法引用（最简洁）
Comparator<String> c4 = Comparator.comparingInt(String::length);
```

四种写法效果完全一样。方法引用的四种形式：

| 形式 | 语法 | 例子 |
|------|------|------|
| 静态方法 | `类名::静态方法` | `Integer::parseInt` |
| 实例方法（特定对象） | `对象::实例方法` | `System.out::println` |
| 实例方法（任意对象） | `类名::实例方法` | `String::length` |
| 构造方法 | `类名::new` | `ArrayList::new` |

---

## 11. 应用五：Comparable 与 Comparator

这是 JDK 里接口设计的经典案例，两个接口分工明确。

| 接口 | 位置 | 方法 | 语义 |
|------|------|------|------|
| `Comparable<T>` | `java.lang` | `int compareTo(T o)` | **内部**比较：我自己知道怎么和同类比 |
| `Comparator<T>` | `java.util` | `int compare(T a, T b)` | **外部**比较：第三方定义比较规则 |

### Comparable：定义"自然顺序"

```java
class Student implements Comparable<Student> {
    String name;
    int score;
    
    @Override
    public int compareTo(Student other) {
        return Integer.compare(this.score, other.score);   // 按分数升序
    }
}

List<Student> list = ...;
Collections.sort(list);     // 不需要额外参数，用自然顺序
```

实现了 `Comparable` 的类才能：

- 直接 `Collections.sort()` / `list.sort(null)` / `Arrays.sort()`
- 作为 `TreeMap` 的 key 或 `TreeSet` 的元素

> **重要约定**：`compareTo` 返回 0 应该和 `equals` 返回 `true` 保持一致。不一致的话，`TreeSet` 和 `HashSet` 对同一组数据的去重结果会不同（`TreeSet` 用 `compareTo`，`HashSet` 用 `equals`+`hashCode`）。

### Comparator：定义"临时规则"

一个类只能有一种自然顺序，但可以有无数种外部比较规则：

```java
List<Student> list = ...;

list.sort(Comparator.comparing(s -> s.name));                   // 按姓名
list.sort(Comparator.comparingInt((Student s) -> s.score).reversed());  // 按分数倒序
```

### 链式比较（实测）

```java
List<String> names = new ArrayList<>(List.of("bob", "alice", "eve", "dan", "al"));

names.sort(Comparator.comparingInt(String::length)
                     .thenComparing(Comparator.naturalOrder()));
// 实测输出: [al, bob, dan, eve, alice]      先按长度，长度相同按字典序

names.sort(Comparator.comparingInt(String::length).reversed());
// 实测输出: [alice, bob, dan, eve, al]      仅按长度倒序
```

注意第二次排序的结果：`bob, dan, eve` 三个长度都是 3，它们之间保持了**上一次排序后的相对顺序** —— 因为 `List.sort` 用的是**稳定排序**（TimSort）。

常用的 `Comparator` 静态/默认方法：

| 方法 | 作用 |
|------|------|
| `comparing(keyExtractor)` | 按某个字段比较 |
| `comparingInt/Long/Double` | 同上，但避免装箱 |
| `thenComparing(...)` | 前一个比较结果相等时的次级规则 |
| `reversed()` | 反转顺序 |
| `naturalOrder()` / `reverseOrder()` | 自然顺序 / 其反序 |
| `nullsFirst(cmp)` / `nullsLast(cmp)` | 处理 null 元素 |

```java
// 实际业务中的典型写法：多级排序 + null 安全
orders.sort(Comparator
    .comparing(Order::getStatus)
    .thenComparing(Order::getCreateTime, Comparator.reverseOrder())
    .thenComparing(Order::getRemark, Comparator.nullsLast(Comparator.naturalOrder())));
```

**这整套流式 API 之所以存在，全靠接口的 `default` 和 `static` 方法。** `Comparator` 是 Java 8 接口新能力的最佳展示。

---

## 12. 应用六：标记接口

**没有任何方法的接口**，作用只是给类打一个"标签"，让运行时能用 `instanceof` 判断。

### JDK 里的三个例子（实测）

```java
"x" instanceof java.io.Serializable        // true   ← String 可序列化
new ArrayList<>() instanceof RandomAccess  // true   ← 支持高效随机访问
new LinkedList<>() instanceof RandomAccess // false  ← 链表不支持
```

| 标记接口 | 含义 | 谁在检查 |
|---------|------|---------|
| `Serializable` | 这个类可以被序列化 | `ObjectOutputStream` 检查，不实现就抛 `NotSerializableException` |
| `Cloneable` | 允许调用 `Object.clone()` | `Object.clone()` 检查，不实现就抛 `CloneNotSupportedException` |
| `RandomAccess` | 随机访问比顺序访问快 | `Collections.binarySearch()` 据此选择算法 |

### RandomAccess 的实际作用

`Collections` 内部会这样分流：

```java
// 伪代码，体现思路
if (list instanceof RandomAccess) {
    for (int i = 0; i < list.size(); i++) { list.get(i); }   // 下标遍历，ArrayList 快
} else {
    Iterator<T> it = list.iterator();                        // 迭代器遍历，LinkedList 快
    while (it.hasNext()) { it.next(); }
}
```

对 `LinkedList` 用下标遍历是 O(n²)，用迭代器是 O(n)。这个标记接口让通用算法能针对不同实现选择最优策略。

### 自己什么时候用标记接口

现在**大多数情况应该用注解（annotation）代替**，因为注解能携带参数、能控制保留策略、不污染类型体系。

标记接口仍然更合适的场景只有一个：**你需要用它做编译期类型约束**。

```java
// 标记接口：编译器能检查
interface Auditable { }
void audit(Auditable target) { }     // 传入非 Auditable 的对象编译就报错

// 注解：只能运行时反射检查
@Audited class Foo { }
void audit(Object target) {
    if (!target.getClass().isAnnotationPresent(Audited.class)) {
        throw new IllegalArgumentException();   // 运行时才发现
    }
}
```

需要"编译期拦住错误调用" → 标记接口。只是打个元数据标签 → 注解。

---

## 13. 应用七：适配器与多重身份

### 适配器：让不兼容的接口协同工作

你有一个老的日志类，签名和团队统一的接口不一致，而且它在第三方 jar 里改不了：

```java
// 第三方的老类，不能改
class LegacyLogger {
    public void writeLog(int level, String text) {
        System.out.println("[" + level + "] " + text);
    }
}

// 团队统一的接口
interface Logger {
    void info(String msg);
    void error(String msg);
}

// 适配器：实现新接口，内部委托给老类
class LegacyLoggerAdapter implements Logger {
    private final LegacyLogger legacy;
    
    LegacyLoggerAdapter(LegacyLogger legacy) { this.legacy = legacy; }
    
    @Override public void info(String msg)  { legacy.writeLog(1, msg); }
    @Override public void error(String msg) { legacy.writeLog(3, msg); }
}
```

现在老类也能当 `Logger` 用了，业务代码完全不知道底下是个老实现。SLF4J 就是靠这个思路同时支持 Log4j、Logback、JUL 的。

### 多重身份：一个类实现多个接口

```java
class SmartDocument implements 
        Serializable,              // 能序列化
        Comparable<SmartDocument>, // 能排序
        Cloneable,                 // 能克隆
        AutoCloseable {            // 能用 try-with-resources
    
    @Override public int compareTo(SmartDocument o) { return 0; }
    @Override public void close() { System.out.println("释放资源"); }
}
```

每个接口赋予一种独立能力，组合起来形成完整的"能力集"。这种细粒度接口的组合，是接口隔离原则（见第 21 节）的直接应用。

### 接口 + 组合替代继承

```java
// ❌ 用继承实现"会飞的鸭子"，很快就会失控
class Duck { }
class FlyingDuck extends Duck { }
class SwimmingDuck extends Duck { }
class FlyingSwimmingDuck extends ??? { }   // 单继承，组合爆炸

// ✅ 用接口表达能力，自由组合
interface Flyable { void fly(); }
interface Swimmable { void swim(); }

class MallardDuck implements Flyable, Swimmable {
    public void fly() { System.out.println("野鸭会飞"); }
    public void swim() { System.out.println("野鸭会游"); }
}
class RubberDuck implements Swimmable {     // 橡皮鸭只会漂，不会飞
    public void swim() { System.out.println("橡皮鸭漂在水上"); }
}
```

**"优先使用组合而非继承"这条经典建议，落地方式往往就是接口。**

---

## 14. 应用八：常量接口（反模式）

先看这种写法 —— 你在老项目里很可能见过：

```java
// ❌ 常量接口：反模式
public interface Constants {
    int MAX_RETRY = 3;
    String DEFAULT_CHARSET = "UTF-8";
    long TIMEOUT_MS = 5000;
}

// 为了"方便"直接实现它
public class HttpClient implements Constants {
    void send() {
        for (int i = 0; i < MAX_RETRY; i++) { }   // 不用写前缀，看起来很爽
    }
}
```

### 为什么是反模式

| 问题 | 说明 |
|------|------|
| **滥用了接口的语义** | 接口应该定义"能做什么"，`HttpClient implements Constants` 读起来毫无意义 |
| **污染了公开 API** | 常量会变成 `HttpClient` 的公开成员，暴露给所有调用方和子类 |
| **污染命名空间** | 类里凭空多出一堆标识符，看代码时搞不清 `MAX_RETRY` 是哪来的 |
| **常量值被编译进调用方** | 基本类型常量会被**内联**到字节码里。改了常量值，不重新编译所有依赖方，旧值依然生效 |
| **无法表达分组和行为** | 一堆散装常量，没有类型安全，`int` 状态码可以传进任何 `int` 参数 |

最后那个"常量内联"的坑尤其隐蔽：你把 `MAX_RETRY` 从 3 改成 5，只重新编译了常量接口，调用方的 class 文件里还是硬编码的 3。

### 正确做法

**方案一：如果是一组有限取值 → 用 `enum`**

```java
public enum OrderStatus {
    CREATED(0, "已创建"),
    PAID(1, "已支付"),
    SHIPPED(2, "已发货");
    
    private final int code;
    private final String desc;
    
    OrderStatus(int code, String desc) { this.code = code; this.desc = desc; }
    
    public int getCode() { return code; }
    public String getDesc() { return desc; }
    
    public static OrderStatus fromCode(int code) {
        for (OrderStatus s : values()) if (s.code == code) return s;
        throw new IllegalArgumentException("未知状态: " + code);
    }
}
```

好处：类型安全（方法签名写 `OrderStatus` 就传不进别的东西）、能带行为、能在 `switch` 里穷尽检查。

**方案二：如果是纯配置常量 → 用不可实例化的工具类**

```java
public final class HttpConstants {
    private HttpConstants() {                     // 私有构造，禁止实例化
        throw new AssertionError("不允许实例化");
    }
    
    public static final int MAX_RETRY = 3;
    public static final String DEFAULT_CHARSET = "UTF-8";
}

// 使用时带类名前缀，来源一目了然
int retry = HttpConstants.MAX_RETRY;
```

**方案三：如果值可能随环境变化 → 放配置文件**

```java
@ConfigurationProperties(prefix = "http.client")
public class HttpClientProperties {
    private int maxRetry = 3;
    private Duration timeout = Duration.ofSeconds(5);
    // getter / setter
}
```

### 接口里到底什么时候可以放常量

当常量**确实是接口契约的一部分**时可以：

```java
interface PayChannel {
    long MIN_AMOUNT_CENTS = 1;      // 所有支付渠道都遵守的下限，属于契约
    PayResult pay(long cents);
}
```

判断标准：**这个常量是不是"实现者必须知道的契约内容"？** 是 → 可以放；只是想找个地方存值 → 用 `enum` 或工具类。

---

## 15. JDK 中的接口设计范例

读 JDK 源码能学到最规范的接口用法。几个值得研究的例子：

### 范例一：接口 + 骨架抽象类 + 具体类（三层结构）

```
Collection（接口）         ← 定义契约，调用方依赖这一层
    ↓
AbstractCollection（抽象类） ← 骨架实现，减少重复代码
    ↓
ArrayList（具体类）         ← 只实现真正和数据结构相关的部分
```

`List` 接口有 20 多个方法。如果没有 `AbstractList`，每个自定义 List 都要实现全部 20 多个。而继承 `AbstractList` 后，**只需实现 `get(int)` 和 `size()` 两个方法**，就能得到一个可用的只读 List：

```java
// 把数组包装成 List，只实现 2 个方法
class ArrayAsList<E> extends AbstractList<E> {
    private final E[] arr;
    ArrayAsList(E[] arr) { this.arr = arr; }
    
    @Override public E get(int i) { return arr[i]; }
    @Override public int size()   { return arr.length; }
    // iterator()、contains()、indexOf()、subList()... 全部由 AbstractList 提供
}
```

**这就是"接口定契约 + 抽象类给骨架"的标准范式。** 看到 JDK 里 `AbstractXxx` 开头的类，基本都是这个用途。

JDK 里的同类结构：`Map`/`AbstractMap`、`Set`/`AbstractSet`、`Queue`/`AbstractQueue`。

### 范例二：用 default 方法平滑演进已有接口

Java 8 要给 `Collection` 加 `stream()` 方法。当时全世界已经有无数个 `implements Collection` 的类 —— 加抽象方法会让它们**全部编译失败**。

`default` 方法就是为解决这个问题而生的：

```java
public interface Collection<E> extends Iterable<E> {
    // ... 原有抽象方法 ...
    
    default Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
    
    default boolean removeIf(Predicate<? super E> filter) {
        // 基于已有的 iterator() 实现，所有实现类自动获得这个能力
        boolean removed = false;
        final Iterator<E> each = iterator();
        while (each.hasNext()) {
            if (filter.test(each.next())) { each.remove(); removed = true; }
        }
        return removed;
    }
}
```

所有已有实现类**不用改一行代码**就获得了 `stream()` 和 `removeIf()`。这是 `default` 方法的设计初衷，也是它最正当的用法。

### 范例三：单方法接口 = 可以直接用 Lambda

```java
@FunctionalInterface
public interface Runnable { void run(); }

// 于是这两种写法等价
new Thread(new Runnable() {
    @Override public void run() { System.out.println("跑"); }
}).start();

new Thread(() -> System.out.println("跑")).start();
```

**设计启示：如果你的接口只需要一个方法，它就自动获得了 Lambda 支持。** 这是把接口设计得"小而专"的额外收益。

### 范例四：AutoCloseable 让语法糖生效

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

实现这个接口，你的类就能用在 `try-with-resources` 里：

```java
class Connection implements AutoCloseable {
    @Override public void close() { System.out.println("连接已关闭"); }
}

try (Connection c = new Connection()) {
    // 使用 c
}   // 自动调用 close()，即使抛异常也会调用
```

**一个接口换来一个语言特性的支持** —— 这说明接口不只是代码组织工具，还是和编译器/语言特性对接的契约。`Iterable` 换来 for-each，`Comparable` 换来 `Collections.sort()`，都是同一回事。

---

# 第三部分：进阶（高级工程师）

## 16. default 方法：动机与四条冲突规则

`default` 方法让接口有了实现，也就让"菱形继承"问题部分回归了。Java 用四条规则解决冲突，**全部实测验证**。

### 规则一：类优先（Class Wins）

**父类的方法永远胜过接口的 default 方法。**

```java
interface A1 { default String hi() { return "A1"; } }
class Parent2 { public String hi() { return "Parent2(类)"; } }
class C2 extends Parent2 implements A1 { }

new C2().hi()   // 实测输出: Parent2(类)
```

设计理由：保证向后兼容。老代码的类继承体系已经确定，Java 8 给接口加 `default` 方法时**不能改变任何已有程序的行为**。如果接口能赢，给 `Collection` 加一个 `default` 方法就可能让某个实现类的行为悄悄改变。

这也解释了第 2 节那条规则 —— `default` 方法不能重写 `toString()`：所有类都继承了 `Object.toString()`，按类优先规则，接口的版本永远赢不了，所以语言规范直接禁止这种写法。

### 规则二：最具体的接口优先（Most Specific Wins）

**子接口的 default 方法胜过父接口的。**

```java
interface A3 { default String hi() { return "A3(父接口)"; } }
interface B3 extends A3 { default String hi() { return "B3(子接口)"; } }
class C3 implements A3, B3 { }

new C3().hi()   // 实测输出: B3(子接口)
```

`C3` 同时声明实现 `A3` 和 `B3`，但 `B3` 是 `A3` 的子接口，更具体，所以 `B3` 赢。这条规则符合直觉：更专门的定义覆盖更通用的定义。

### 规则三：无法判断时，必须显式重写

**两个无关接口有同名 default 方法 → 编译错误，必须自己重写。**

```java
interface X1 { default String hi() { return "X1"; } }
interface Y1 { default String hi() { return "Y1"; } }
class Z1 implements X1, Y1 { }
```

实测编译错误：

```
错误: 类型 X1 和 Y1 不兼容；
  类 Z1从类型 X1 和 Y1 中继承了hi() 的不相关默认值
```

**Java 的选择是"报错，让程序员决定"，而不是随便挑一个。** 这是个好设计 —— 隐式选择会埋下难查的 bug。

### 规则四：用 `接口名.super.方法()` 指定

重写时可以调用指定接口的实现：

```java
class C1 implements A1, B1 {
    @Override
    public String hi() {
        return A1.super.hi() + "+" + B1.super.hi();   // 显式指定
    }
}

new C1().hi()   // 实测输出: A1+B1
```

语法是 `接口名.super.方法名()`，注意和类的 `super.方法名()` 区别开。

### 附加规则：抽象方法 vs default 方法

如果一个接口声明了抽象方法，另一个提供了同名 default 方法，**也必须重写**：

```java
interface A4 { default String hi() { return "A4"; } }
interface B4 { String hi(); }                    // 抽象方法
class C4 implements A4, B4 {
    @Override public String hi() { return "C4必须自己实现"; }
}

new C4().hi()   // 实测输出: C4必须自己实现
```

不能让 `A4` 的 default 实现自动满足 `B4` 的抽象声明 —— `B4` 明确要求"实现类必须自己提供实现"，这个要求必须被尊重。

### 四条规则速查表

| 情形 | 结果 |
|------|------|
| 父类方法 vs 接口 default | **父类赢**（类优先） |
| 子接口 default vs 父接口 default | **子接口赢**（最具体者优先） |
| 两个无关接口的 default 同名 | **编译错误**，必须重写 |
| 接口抽象方法 vs 另一接口 default 同名 | **编译错误**，必须重写 |
| 重写时想复用某个接口的实现 | `接口名.super.方法()` |

### default 方法的代价（设计层面的思考）

`default` 方法很方便，但它有隐性成本：

1. **破坏了"接口只是契约"的纯粹性。** 接口现在既是契约又有实现，职责不单一。
2. **容易被滥用成多继承。** 有人用它变相实现 Mixin，结果是把实现逻辑散落在多个接口里，调试时很难追踪方法到底来自哪。
3. **无状态限制让它能力有限。** `default` 方法不能访问实例字段，只能基于其他接口方法工作，复杂逻辑写不了。
4. **一旦发布就很难移除。** 和抽象方法一样是公开 API。

**实践建议**：`default` 方法主要用于两个场合 —— **给已发布的接口做兼容性扩展**，以及**提供基于其他抽象方法的便利方法**（像 `Comparator.reversed()`、`Predicate.and()`）。不要用它当"代码复用"的主要手段，那是抽象类的活。

---

## 17. static 与 private 接口方法

### static 方法（Java 8+）：不被继承

```java
interface A5 {
    static String stat() { return "A5.stat"; }
}
class C5 implements A5 { }
```

实测：

```java
A5.stat()       // "A5.stat"   ← 必须用接口名调用
C5.stat()       // 编译错误    ← 实现类继承不到
```

**接口的 static 方法不会被实现类继承**（和类的 static 方法不同）。这是刻意设计的 —— 避免多个接口的 static 方法同名时又产生冲突。

典型用途是**提供接口相关的工厂方法和工具方法**，把它们和接口放在一起，不再需要额外的 `XxxUtils` 类：

```java
interface Validator<T> {
    boolean validate(T t);
    
    // 工厂方法：创建常用实现
    static <T> Validator<T> notNull() {
        return Objects::nonNull;
    }
    
    static Validator<String> notBlank() {
        return s -> s != null && !s.isBlank();
    }
    
    // 组合工具
    static <T> Validator<T> allOf(List<Validator<T>> validators) {
        return t -> validators.stream().allMatch(v -> v.validate(t));
    }
}

// 使用
Validator<String> v = Validator.allOf(List.of(Validator.notBlank(), s -> s.length() < 20));
```

JDK 里 `List.of()`、`Map.of()`、`Comparator.comparing()`、`Stream.of()` 全是接口 static 方法。

### private 方法（Java 9+）：抽取公共逻辑

Java 8 的 `default` 方法只能是 `public`，导致多个 `default` 方法共用的辅助逻辑**无处安放** —— 要么重复写，要么暴露成公开方法污染 API。

Java 9 允许接口有 `private` 方法：

```java
interface A5 {
    default String callPrivate() { return helper() + "(经由default调用)"; }
    private String helper() { return "A5私有方法"; }   // Java 9+
}
```

实测：

```java
new C5().callPrivate()   // "A5私有方法(经由default调用)"
```

规则：

- `private` 方法**必须有方法体**（不能是抽象的）
- 只能被同一接口内的 `default` / `static` / 其他 `private` 方法调用
- 实现类**完全看不到**它，不污染公开 API
- 也可以是 `private static`

实用场景：

```java
interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    
    default V getOrCompute(K key, Function<K, V> loader) {
        V v = get(key);
        if (v == null) { v = loader.apply(key); validateAndPut(key, v); }
        return v;
    }
    
    default void putAll(Map<K, V> map) {
        map.forEach(this::validateAndPut);
    }
    
    // 两个 default 方法共用的校验逻辑，不想暴露出去
    private void validateAndPut(K key, V value) {
        if (key == null) throw new IllegalArgumentException("key 不能为 null");
        put(key, value);
    }
}
```

---

## 18. SPI 与 ServiceLoader

**SPI（Service Provider Interface）是 JDK 内置的插件化机制**：定义接口，由外部提供实现，运行时动态发现。这是接口在框架设计中最重要的应用。

### 完整可运行示例（已实测）

**第一步：定义服务接口**

```java
// com/example/spi/PayChannel.java
package com.example.spi;

public interface PayChannel {
    String name();
    boolean supports(String code);
    String pay(long cents);
}
```

**第二步：提供两个实现**

```java
package com.example.spi;

public class AliPay implements PayChannel {
    public String name() { return "支付宝"; }
    public boolean supports(String code) { return "ALIPAY".equals(code); }
    public String pay(long cents) { return "支付宝扣款 " + cents / 100.0 + " 元"; }
}

public class WeChatPay implements PayChannel {
    public String name() { return "微信支付"; }
    public boolean supports(String code) { return "WECHAT".equals(code); }
    public String pay(long cents) { return "微信扣款 " + cents / 100.0 + " 元"; }
}
```

**第三步：注册（关键）**

创建文件 `META-INF/services/com.example.spi.PayChannel`：

```
com.example.spi.AliPay
com.example.spi.WeChatPay
```

**文件名必须是接口的全限定名**，内容是实现类的全限定名，每行一个。

**第四步：用 ServiceLoader 发现实现**

```java
import java.util.ServiceLoader;

ServiceLoader<PayChannel> loader = ServiceLoader.load(PayChannel.class);
for (PayChannel c : loader) {
    System.out.println("发现实现: " + c.name() + " -> " + c.getClass().getName());
}
```

实测输出：

```
=== SPI：ServiceLoader 发现接口的所有实现 ===
  发现实现: 支付宝 -> com.example.spi.AliPay
  发现实现: 微信支付 -> com.example.spi.WeChatPay
=== 按渠道编码路由 ===
  ALIPAY -> 支付宝扣款 99.0 元
  WECHAT -> 微信扣款 99.0 元
  UNKNOWN -> 无可用渠道
```

### SPI 的价值：真正的"零侵入扩展"

对比一下三种扩展方式的侵入程度：

| 方式 | 新增实现时需要改什么 |
|------|-------------------|
| `if-else` | 改核心业务代码 ❌ |
| 策略模式 + 手动注册 | 改注册代码 ⚠️ |
| 策略模式 + Spring 扫描 | 加个 `@Component` 注解 ✅ |
| **SPI** | **加一个 jar 包，核心代码和配置都不用改** ✅✅ |

SPI 最强的地方是**跨 jar 扩展**：你的框架定义接口，第三方在自己的 jar 里实现并注册，用户只要把那个 jar 放进 classpath，功能就生效了。

### JDK 和主流框架中的 SPI

| 场景 | 接口 | 说明 |
|------|------|------|
| JDBC 驱动 | `java.sql.Driver` | 这就是为什么现代 JDBC **不需要** `Class.forName("com.mysql.jdbc.Driver")` |
| 日志门面 | `org.slf4j.spi.SLF4JServiceProvider` | SLF4J 2.x 靠 SPI 找到 Logback/Log4j |
| Spring Boot | `META-INF/spring/....imports` | 自动配置（3.x 的新格式，思路同源） |
| Dubbo | 自研增强版 SPI | 支持按名称取、依赖注入、AOP |

### 原生 SPI 的局限

| 局限 | 说明 |
|------|------|
| **全部实例化** | `ServiceLoader` 遍历时会创建所有实现的实例，无法按需加载 |
| **无法按名获取** | 只能遍历，想拿特定实现得自己过滤（像示例里的 `supports()`） |
| **不支持依赖注入** | 实现类拿不到容器里的其他 bean |
| **非线程安全** | `ServiceLoader` 实例本身不是线程安全的 |
| **必须有无参构造** | 实现类不能有带参构造方法 |

这些局限正是 Dubbo、Spring 要自己增强 SPI 的原因。

### 一个实用改进：缓存 + 按名索引

```java
public final class PayChannelRegistry {
    private static final Map<String, PayChannel> CACHE;
    
    static {
        Map<String, PayChannel> m = new HashMap<>();
        for (PayChannel c : ServiceLoader.load(PayChannel.class)) {
            m.put(c.name(), c);                    // 只加载一次，建立索引
        }
        CACHE = Map.copyOf(m);                     // 不可变，线程安全
    }
    
    private PayChannelRegistry() { }
    
    public static Optional<PayChannel> get(String name) {
        return Optional.ofNullable(CACHE.get(name));
    }
}
```

注意上面示例里为了演示，每次循环都调用了 `ServiceLoader.load()`，**这在生产代码里是性能问题** —— 每次调用都会重新扫描 classpath 并重新实例化所有实现。实际应该像这样加载一次后缓存。

---

## 19. 为什么 JDK 动态代理只能代理接口

这是理解 Spring AOP 的关键知识点。

### 实测：JDK 动态代理

```java
interface UserService {
    String findById(long id);
    void delete(long id);
}

class LogProxy implements InvocationHandler {
    private final Object target;
    
    @SuppressWarnings("unchecked")
    static <T> T wrap(T target, Class<T> itf) {
        return (T) Proxy.newProxyInstance(
                itf.getClassLoader(), new Class<?>[]{itf}, new LogProxy(target));
    }
    
    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        long t = System.nanoTime();
        Object r = m.invoke(target, args);
        System.out.printf("[日志] %s 耗时 %d ns%n", m.getName(), System.nanoTime() - t);
        return r;
    }
}
```

实测输出：

```
[日志] findById 耗时 21410692 ns
返回: User#42
    真实删除 42
[日志] delete 耗时 2758221 ns
代理对象的类名: $Proxy0
它是 UserService 吗: true
它是 UserServiceImpl 吗: false
```

三个关键观察：

1. 代理类的名字是 **`$Proxy0`** —— 运行时动态生成的类
2. `svc instanceof UserService` 是 **`true`** —— 它实现了接口
3. `svc instanceof UserServiceImpl` 是 **`false`** —— 它**不是**原实现类的子类

### 为什么必须是接口

动态生成的 `$Proxy0` 类，结构大致是：

```java
public final class $Proxy0 extends java.lang.reflect.Proxy implements UserService {
    public String findById(long id) {
        return (String) super.h.invoke(this, m3, new Object[]{id});
    }
    // ...
}
```

关键在第一行：**`$Proxy0` 已经 `extends Proxy` 了**。

`Proxy` 类里存放着 `InvocationHandler h` 这个字段，生成的代理类必须继承它才能拿到 handler。而 Java 是**单继承** —— 继承槽位已经被 `Proxy` 占用，**没法再继承你的目标类**，只能通过 `implements` 实现接口。

这就是"JDK 动态代理只能代理接口"的根本原因：**不是设计者的偏好，是单继承的硬限制。**

### 对应到 Spring AOP

Spring AOP 有两种代理方式：

| 代理方式 | 要求 | 原理 |
|---------|------|------|
| **JDK 动态代理** | 目标类**必须实现接口** | 生成实现同样接口的 `$ProxyN` 类 |
| **CGLIB** | 目标类**不能是 final** | 生成目标类的**子类**，重写方法 |

Spring 的选择逻辑（Spring Boot 2.x 起默认 `proxyTargetClass=true`，统一用 CGLIB）：

- 有接口 → 默认 JDK 代理（除非强制指定 CGLIB）
- 无接口 → 自动用 CGLIB

### 两个实际会踩的坑

**坑一：注入时用实现类类型会失败**

```java
@Service
public class UserServiceImpl implements UserService {
    @Transactional
    public void doSomething() { }
}

@Autowired
private UserServiceImpl userService;   // ❌ 用 JDK 代理时报错：找不到匹配的 bean
```

因为容器里实际放的是 `$Proxy0`，它只是 `UserService`，**不是** `UserServiceImpl`（实测确认 `instanceof UserServiceImpl` 为 `false`）。

**正确做法：按接口注入。**

```java
@Autowired
private UserService userService;   // ✅
```

**这也是"面向接口编程"在框架层面的硬性要求**，不只是风格建议。

**坑二：类内部自调用导致 AOP 失效**

```java
@Service
public class OrderServiceImpl implements OrderService {
    
    public void batchProcess() {
        for (Order o : orders) {
            process(o);        // ❌ this.process()，走的是原对象，不经过代理
        }
    }
    
    @Transactional
    public void process(Order o) { }   // 事务注解失效！
}
```

`this.process()` 调的是**原始对象**的方法，没有经过代理对象，所以 `@Transactional`、`@Cacheable`、`@Async` 这些基于代理的注解**全部失效**。

解法（按推荐程度排序）：

```java
// 方案 1（推荐）：拆到另一个 bean 里，跨 bean 调用会经过代理
@Service
public class OrderBatchService {
    private final OrderProcessor processor;   // 注入另一个 bean
    public void batchProcess() { orders.forEach(processor::process); }
}

// 方案 2：注入自己的代理（Spring 允许自注入）
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired @Lazy private OrderService self;
    public void batchProcess() { orders.forEach(self::process); }
}

// 方案 3：手动取代理（需开启 exposeProxy，不推荐，耦合框架 API）
((OrderService) AopContext.currentProxy()).process(o);
```

**理解"代理是另一个对象"这件事，比记住解法更重要。** 很多"事务不生效""缓存不生效"的诡异问题根源都在这里。

---

## 20. 密封接口 sealed interface

Java 17 正式引入。**密封接口精确限定哪些类可以实现它**，把开放的继承体系变成封闭的代数数据类型。

### 实测示例

```java
sealed interface Shape permits Circle, Square, Rect { }

record Circle(double r) implements Shape { }
record Square(double side) implements Shape { }
record Rect(double w, double h) implements Shape { }
```

### 最大收益：switch 的穷尽性检查

```java
static double area(Shape s) {
    return switch (s) {
        case Circle c -> Math.PI * c.r() * c.r();
        case Square q -> q.side() * q.side();
        case Rect r   -> r.w() * r.h();
        // 不需要 default 分支！编译器知道已经穷尽了所有可能
    };
}
```

实测输出：

```
Circle[r=1.0]          面积 = 3.1416
Square[side=2.0]       面积 = 4.0000
Rect[w=2.0, h=3.0]     面积 = 6.0000
Shape 是密封的吗: true
允许的子类: [class Circle, class Square, class Rect]
```

**关键价值**：如果以后给 `Shape` 加一个 `Triangle`，这个 `switch` 会**立即编译报错**，提醒你去补上新分支。普通接口做不到这一点 —— 你只能写个 `default` 分支抛异常，等运行时才发现漏了。

| | 普通接口 | 密封接口 |
|---|---------|---------|
| 谁能实现 | 任何类 | 只有 `permits` 列出的 |
| switch 是否需要 default | 需要 | **不需要** |
| 漏处理新类型时 | 运行时才发现 | **编译期报错** |

### 语法规则

```java
// 1. 子类型必须显式声明（且和接口在同一模块/包）
sealed interface Shape permits Circle, Square { }

// 2. 实现类必须是 final、sealed 或 non-sealed 之一
record Circle(double r) implements Shape { }        // record 隐含 final
final class Square implements Shape { }             // 显式 final
sealed interface Polygon extends Shape permits Rect { }   // 继续密封
non-sealed class OpenShape implements Shape { }     // 显式解除密封，允许任意继承

// 3. 同一文件内可以省略 permits（编译器自动推断）
sealed interface Expr { }
record Num(int v) implements Expr { }
record Add(Expr l, Expr r) implements Expr { }
```

### 典型应用：表达结果类型

比 `Optional` 或抛异常更精确地表达"可能的几种结果"：

```java
sealed interface Result<T> permits Success, Failure { }
record Success<T>(T value) implements Result<T> { }
record Failure<T>(String errorCode, String message) implements Result<T> { }

// 调用方被编译器强制处理所有情况
String describe(Result<User> r) {
    return switch (r) {
        case Success<User> s -> "成功: " + s.value().getName();
        case Failure<User> f -> "失败[" + f.errorCode() + "]: " + f.message();
    };
}
```

### 应用：表达式求值（代数数据类型）

密封接口 + record + 模式匹配，能让递归数据结构写得非常干净：

```java
sealed interface Expr { }
record Num(int value) implements Expr { }
record Add(Expr left, Expr right) implements Expr { }
record Mul(Expr left, Expr right) implements Expr { }
record Neg(Expr inner) implements Expr { }

static int eval(Expr e) {
    return switch (e) {
        case Num n  -> n.value();
        case Add a  -> eval(a.left()) + eval(a.right());
        case Mul m  -> eval(m.left()) * eval(m.right());
        case Neg n  -> -eval(n.inner());
    };
}

// (2 + 3) * -4 = -20
int r = eval(new Mul(new Add(new Num(2), new Num(3)), new Neg(new Num(4))));
```

这种写法在解析器、状态机、规则引擎里非常好用。

### 什么时候用密封接口

| 用密封接口 | 用普通接口 |
|-----------|-----------|
| 实现类的集合**稳定、有限、已知** | 期望第三方自由扩展 |
| 需要 switch 穷尽检查 | 用多态分发而非 switch |
| 建模"几种互斥的情况" | 建模"一种能力" |
| 领域模型、AST、状态、结果类型 | SPI、插件、策略 |

**判断标准：你希望别人扩展它吗？** 希望 → 普通接口；不希望、且想在新增类型时让所有使用点编译报错 → 密封接口。

注意密封接口和 SPI/策略模式**目标相反** —— 前者封闭扩展换取编译期安全，后者开放扩展换取灵活性。选错方向会很别扭。

---

## 21. 接口设计的工程原则

### 原则一：接口隔离（ISP）—— 宁小勿大

**不要让实现类被迫实现它用不到的方法。**

```java
// ❌ 胖接口：所有实现类都被迫实现全部方法
interface Worker {
    void work();
    void eat();
    void sleep();
    void getSalary();
}

class Robot implements Worker {
    public void work() { }
    public void eat() { throw new UnsupportedOperationException(); }      // 机器人不吃饭
    public void sleep() { throw new UnsupportedOperationException(); }    // 不睡觉
    public void getSalary() { throw new UnsupportedOperationException(); }// 不领工资
}
```

三个 `UnsupportedOperationException` 就是接口设计失败的信号。拆开：

```java
// ✅ 细粒度接口，按需组合
interface Workable { void work(); }
interface Feedable { void eat(); }
interface Payable  { void getSalary(); }

class Human implements Workable, Feedable, Payable { /* 全部实现 */ }
class Robot implements Workable { /* 只实现需要的 */ }
```

**自检信号**：如果实现类里出现 `throw new UnsupportedOperationException()` 或者空方法体，接口大概率该拆了。

> JDK 自己也踩过这个坑：`List.add()` 在不可变列表上抛 `UnsupportedOperationException`。这是历史包袱，不是好设计 —— 别学。

### 原则二：接口归属于调用方，不归属于实现方

这一点很反直觉，但很重要。

```
// ❌ 常见的错误组织方式：接口放在实现所在的模块
infrastructure/
    OrderRepository.java        （接口）
    MySqlOrderRepository.java   （实现）
domain/
    OrderService.java           → 依赖 infrastructure 模块

// ✅ 正确：接口放在使用它的模块
domain/
    OrderRepository.java        （接口，由业务定义"我需要什么"）
    OrderService.java
infrastructure/
    MySqlOrderRepository.java   → 依赖 domain 模块
```

接口应该表达**调用方的需求**，而不是实现方的能力。放对了位置，模块依赖方向才是从外向内（基础设施依赖业务），这是整洁架构/六边形架构的核心。

实际判断方法：**如果换掉实现要改接口，说明接口定义倒向实现了。**

### 原则三：接口方法的参数和返回值也要面向抽象

```java
// ❌ 返回具体实现类，调用方被绑死
interface UserRepository {
    ArrayList<User> findAll();          // 为什么必须是 ArrayList？
    void saveAll(ArrayList<User> users);
}

// ✅ 返回接口类型
interface UserRepository {
    List<User> findAll();
    void saveAll(Collection<User> users);   // 参数用更宽泛的类型，调用方更自由
}
```

经验法则：**参数类型尽量宽（`Collection` > `List` > `ArrayList`），返回类型适度具体但仍是接口（返回 `List` 而不是 `Collection`，方便调用方按下标访问）。**

### 原则四：已发布的接口不要加抽象方法

```java
// 1.0 版本
public interface Plugin {
    void execute();
}

// 2.0 想加初始化方法
public interface Plugin {
    void execute();
    void init();          // ❌ 所有已有实现类全部编译失败
}
```

三种兼容的演进方式：

```java
// ✅ 方案 1：用 default 方法，给个合理的空实现
public interface Plugin {
    void execute();
    default void init() { }      // 已有实现不受影响
}

// ✅ 方案 2：新建子接口
public interface InitializablePlugin extends Plugin {
    void init();
}
// 调用方：if (p instanceof InitializablePlugin ip) ip.init();

// ✅ 方案 3：新建独立接口，让需要的实现类多实现一个
public interface Initializable { void init(); }
```

**选择建议**：方法对所有实现都有意义且能给出合理默认值 → `default`；只对部分实现有意义 → 独立接口。

### 原则五：命名表达能力，不表达实现

| 不好 | 好 | 理由 |
|------|-----|------|
| `IUserService` | `UserService` | Java 不用匈牙利命名法，`I` 前缀是 C#/Delphi 习惯 |
| `UserServiceInterface` | `UserService` | 后缀冗余 |
| `MySqlUserDao`（作接口名） | `UserRepository` | 接口名不该暴露实现技术 |
| `Manager`、`Helper`、`Processor` | `OrderValidator`、`PriceCalculator` | 模糊的名字说明职责不清 |

实现类的命名则可以体现技术细节：`JdbcUserRepository`、`RedisUserRepository`、`InMemoryUserRepository`。

**JDK 的命名惯例值得参考**：

- 能力型接口用 `-able` 后缀：`Runnable`、`Comparable`、`Serializable`、`Closeable`、`Iterable`
- 名词表示角色：`List`、`Map`、`Collection`、`Iterator`

### 原则六：不要为了"将来可能要换"而给每个类加接口

这是对"面向接口编程"最常见的误读：

```java
// ❌ 过度设计：只有一个实现，而且根本不会有第二个
interface UserDtoConverter { UserDto convert(User u); }
class UserDtoConverterImpl implements UserDtoConverter { }

interface DateFormatHelper { String format(Date d); }
class DateFormatHelperImpl implements DateFormatHelper { }
```

一个接口配一个 `XxxImpl`，而且永远只会有这一个实现 —— 这只是增加了文件数量和跳转成本，没有任何收益。

**该加接口的信号（满足一条即可）：**

- 已经有或**明确即将有**第二个实现
- 需要在测试中替换掉它（涉及数据库、网络、时间、随机数等外部依赖）
- 它是模块/包的对外边界，需要隔离内部变化
- 需要动态代理（事务、缓存、重试等 AOP 能力）

**不该加的信号：**

- 纯数据转换、纯计算、纯工具方法
- 只有一个实现且逻辑稳定
- `XxxImpl` 是你能想到的唯一实现类名

> 顺带一提：`XxxImpl` 这个命名本身就是个信号 —— 如果你想不出比 `Impl` 更有意义的实现类名，说明这个抽象可能不必要。真正有价值的抽象，实现类会有自然的名字（`MySqlOrderRepository`、`RedisOrderRepository`）。

---

# 收尾

## 22. 常见误区

### 初级误区

| 误区 | 正确理解 |
|------|---------|
| 接口里只能有抽象方法 | Java 8+ 还能有 `default`、`static`，Java 9+ 能有 `private` |
| 接口不能有任何实现 | `default` 和 `static` 方法都有实现 |
| 实现接口方法时可以不写 `public` | 必须写，接口方法隐含 `public`，重写不能缩小权限 |
| 接口可以有实例字段 | 只能有 `public static final` 常量，**不能有状态** |
| 接口字段要写全 `public static final` | 隐含的，写了是冗余（实测确认） |
| 接口可以有构造方法 | 不能，接口无法实例化也没有实例字段 |
| 类实现接口用 `extends` | 类实现接口用 `implements`；**接口继承接口才用 `extends`** |
| 一个类只能实现一个接口 | 可以实现多个，这是接口相对抽象类的核心优势 |

### 中级误区

| 误区 | 正确理解 |
|------|---------|
| 有了 `default` 方法，接口可以完全替代抽象类 | 接口**没有状态、没有构造方法、不能 `final` 方法**，模板方法模式只能用抽象类 |
| 每个类都该配一个接口 | 只有一个实现且不需要测试替换时，接口是纯负担（见第 21 节原则六） |
| 接口应该设计得大而全 | 相反，应该小而专（接口隔离原则） |
| 常量放接口里很方便 | 反模式，用 `enum` 或工具类（见第 14 节） |
| 函数式接口必须加 `@FunctionalInterface` | 不是必须，但建议加，让编译器帮你检查 |
| 函数式接口只能有一个方法 | 只能有一个**抽象**方法，`default`/`static` 可以有多个 |
| `IUserService` 是规范命名 | Java 不用 `I` 前缀，直接叫 `UserService` |
| 接口名应该体现实现技术 | 接口名体现**能力**，技术细节放实现类名里 |

### 高级误区

| 误区 | 正确理解 |
|------|---------|
| 两个接口有同名 default 方法，编译器会自动选一个 | **编译错误**，必须显式重写（实测确认） |
| 接口 default 方法能覆盖父类方法 | 不能，**类优先**（实测：父类方法赢） |
| 接口的 `static` 方法能被实现类继承 | 不能，只能用接口名调用（实测确认） |
| `default` 方法可以重写 `toString()` | 编译错误（实测确认报错信息） |
| JDK 动态代理可以代理普通类 | 只能代理接口，因为 `$ProxyN` 已经 `extends Proxy`，单继承槽位被占 |
| 注入时用实现类类型没问题 | 用 JDK 代理时会失败，代理对象 **不是** 实现类的实例（实测 `instanceof` 为 `false`） |
| 类内部 `this.method()` 调用能触发 AOP | 不能，绕过了代理对象，`@Transactional` 等注解失效 |
| 密封接口限制了灵活性，不该用 | 建模"有限几种情况"时，它换来的编译期穷尽检查价值很大 |
| SPI 每次用 `ServiceLoader.load()` 没问题 | 每次都会重新扫描并实例化所有实现，必须缓存 |

### 一道综合判断题

```java
interface A { default String who() { return "A"; } }
interface B { default String who() { return "B"; } }
interface C extends A { default String who() { return "C"; } }
class P        { public String who() { return "P"; } }

class T1 implements A, B { }            // ① 能编译吗？
class T2 implements A, C { }            // ② 输出什么？
class T3 extends P implements A { }     // ③ 输出什么？
```

答案：

- **① 编译错误** —— `A` 和 `B` 是无关接口，default 方法冲突，必须显式重写
- **② `C`** —— `C` 是 `A` 的子接口，更具体者优先
- **③ `P`** —— 类优先原则，父类方法胜过接口 default

---

## 23. 分级练习题

### 初级（巩固语法）

**1.** 定义接口 `Shape`，包含 `double area()` 和 `double perimeter()`，再加一个 `default void print()` 打印名称和两个值。实现 `Circle` 和 `Rectangle`，用 `List<Shape>` 遍历输出。

**2.** 下面每段代码能否编译？不能的说明原因：

```java
// A
interface I1 { void m(); }
class C1 implements I1 { void m() { } }

// B
interface I2 { int x = 1; }
class C2 implements I2 { void m() { x = 2; } }

// C
interface I3 { I3() { } void m(); }

// D
interface I4 { private void helper() { } default void m() { helper(); } }

// E
interface I5 { static void s() { } }
class C5 implements I5 { void m() { C5.s(); } }
```

**3.** 把这个抽象类改成接口，说明哪些成员改不过去、为什么：

```java
abstract class Cache {
    protected Map<String, Object> store = new HashMap<>();
    public Cache() { System.out.println("初始化"); }
    public final void put(String k, Object v) { store.put(k, v); }
    public abstract void evict();
}
```

### 中级（应用场景）

**4.** 用策略模式重构下面的代码，要求新增折扣类型时不修改 `PriceCalculator`：

```java
class PriceCalculator {
    double calc(String type, double price) {
        if ("NONE".equals(type))        return price;
        else if ("VIP".equals(type))    return price * 0.8;
        else if ("FULL_100".equals(type)) return price >= 100 ? price - 20 : price;
        else if ("NEW_USER".equals(type)) return Math.max(price - 10, 0);
        throw new IllegalArgumentException(type);
    }
}
```

**5.** 设计一个重试工具，签名如下。思考：为什么参数用 `Supplier<T>` 和 `Predicate<Exception>` 而不是自定义接口？

```java
static <T> T retry(Supplier<T> action, int maxAttempts, Predicate<Exception> retryable);
```

**6.** 给 `Employee`（字段：`department`、`salary`、`hireDate`、`name`）写一个 `Comparator`，要求：按部门升序 → 同部门按薪资降序 → 薪资相同按入职时间升序 → 入职时间也相同按姓名字典序。用 `Comparator` 的链式 API，不要写 `if-else`。

**7.** 实现一个事件总线，支持注册监听器和发布事件。要求用泛型接口，使得 `OrderCreatedEvent` 的监听器不会收到 `UserRegisteredEvent`。

```java
interface EventListener<E> { void onEvent(E event); }
class EventBus {
    <E> void subscribe(Class<E> type, EventListener<E> listener) { }
    <E> void publish(E event) { }
}
```

**8.** 判断下列情形该用接口还是抽象类，并说明理由：

| 情形 | 接口 / 抽象类 |
|------|-------------|
| 所有 DAO 都需要一个 `DataSource` 字段和连接管理逻辑 | ? |
| 定义"可导出为 Excel"的能力 | ? |
| 所有报表的生成流程固定为「查数据→算汇总→渲染→输出」，只有"算汇总"各不相同 | ? |
| 定义支付渠道的契约，未来由第三方 jar 提供实现 | ? |

### 高级（机制与设计）

**9.** 预测输出并解释规则（不要运行，先自己推）：

```java
interface A { default String f() { return "A"; } }
interface B extends A { default String f() { return "B"; } }
interface C extends A { default String f() { return "C"; } }
class D implements B, C {
    public String f() { return B.super.f() + C.super.f() + "D"; }
}
// new D().f() = ?
// 如果 D 不重写 f()，会怎样？
```

**10.** 解释为什么下面的 `@Transactional` 不生效，给出三种修复方案并比较优劣：

```java
@Service
public class ReportServiceImpl implements ReportService {
    public void generateAll(List<Long> ids) {
        for (Long id : ids) generateOne(id);
    }
    @Transactional
    public void generateOne(Long id) { /* 写库 */ }
}
```

**11.** 用 SPI 实现一个可插拔的文件解析器：定义 `FileParser` 接口（含 `supports(String ext)` 和 `parse(InputStream)`），提供 CSV 和 JSON 两个实现，用 `ServiceLoader` 发现。要求解决原生 SPI 的两个问题：**重复加载**和**按扩展名快速查找**。

**12.** 用密封接口建模一个 HTTP 响应，要求调用方必须处理所有情况，漏处理时编译报错：

```java
// 需要表达：2xx 成功（带 body）、4xx 客户端错误（带错误码和消息）、
//          5xx 服务端错误（带异常）、超时（带已等待毫秒数）
```

**13.** 设计题：你要为团队设计一个缓存抽象，需求是 ① 支持本地缓存和 Redis 两种实现 ② 业务代码不感知实现 ③ 要能单元测试 ④ 以后可能加"二级缓存"（先查本地再查 Redis）。请给出接口定义和类结构，并说明哪里用接口、哪里用抽象类、为什么。

<details>
<summary>第 2 题参考答案</summary>

| 代码 | 能否编译 | 原因 |
|------|---------|------|
| A | ❌ | 实现接口方法必须是 `public`，漏写就是缩小访问权限 |
| B | ❌ | `x` 隐含 `public static final`，不能赋值（实测报错："无法为 static final 变量 x 分配值"） |
| C | ❌ | 接口不能有构造方法 |
| D | ✅ | Java 9+ 允许 `private` 接口方法，且可被 `default` 方法调用 |
| E | ❌ | 接口的 `static` 方法不被实现类继承，必须写 `I5.s()` |

</details>

<details>
<summary>第 3 题参考答案</summary>

三处改不过去：

1. **`protected Map store`** —— 接口不能有实例字段。改成接口后，这个共享状态必须下移到每个实现类，造成重复。
2. **构造方法** —— 接口没有构造方法，初始化逻辑无处安放。
3. **`final void put()`** —— 接口的 `default` 方法不能声明 `final`，无法阻止实现类重写，流程锁不住。

此外 `protected` 也不行，接口方法只能是 `public`（或 Java 9+ 的 `private`）。

**结论**：这个类有状态、有初始化逻辑、要锁定流程，**应该保持为抽象类**。正确做法是接口 + 抽象类配合：

```java
interface Cache {                       // 契约
    void put(String k, Object v);
    void evict();
}

abstract class AbstractCache implements Cache {   // 骨架 + 状态
    protected final Map<String, Object> store = new HashMap<>();
    @Override public final void put(String k, Object v) { store.put(k, v); }
}
```

</details>

<details>
<summary>第 9 题参考答案</summary>

`new D().f()` 输出 **`BCD`**。

`B.super.f()` 返回 `"B"`，`C.super.f()` 返回 `"C"`，拼上 `"D"`。

**如果 `D` 不重写 `f()`：编译错误。**

虽然 `B` 和 `C` 都继承自 `A`，但它们**各自重写了 `f()`**，相互之间没有继承关系 —— 对 `D` 来说，`B.f()` 和 `C.f()` 是两个"不相关的默认值"，"最具体接口优先"规则无法分出胜负（`B` 和 `C` 同级）。

报错信息和第 16 节规则三实测的一致：

```
错误: 类型 B 和 C 不兼容；
  类 D从类型 B 和 C 中继承了f() 的不相关默认值
```

这是**真正的菱形继承问题**，Java 的处理方式是拒绝编译、强制程序员显式决策。

</details>

<details>
<summary>第 10 题参考答案</summary>

**不生效的原因**：`generateAll` 里调用 `generateOne(id)` 等价于 `this.generateOne(id)`，走的是**原始对象**的方法，没有经过 Spring 生成的代理对象。`@Transactional` 的事务开启逻辑在代理的 `invoke` 里，被完全绕过了。

| 方案 | 做法 | 优劣 |
|------|------|------|
| **1. 拆分 bean**（推荐） | 把 `generateOne` 移到另一个 `@Service`，注入后调用 | ✅ 职责清晰，无框架耦合；❌ 多一个类 |
| **2. 自注入** | `@Autowired @Lazy private ReportService self;` 然后调 `self.generateOne()` | ✅ 改动小；❌ 自引用可读性差，需要 `@Lazy` 避免循环依赖 |
| **3. `AopContext.currentProxy()`** | 需配置 `@EnableAspectJAutoProxy(exposeProxy = true)` | ❌ 业务代码耦合 Spring AOP API，最不推荐 |

**另外要注意**：即使修好了，这里每条记录一个独立事务可能也不是想要的语义。如果要整批原子化，应该把 `@Transactional` 加在 `generateAll` 上；如果要单条失败不影响其他，才是现在这种设计。**先想清楚事务边界，再谈怎么让注解生效。**

</details>

---

## 一句话总结

**接口是契约**：它声明"能做什么"，不关心"怎么做"，从而把变化隔离在契约之后。

### 三个层次的要点

**初级 —— 会用：**

1. 接口定方法签名，实现类用 `implements` 并加 `public` 实现全部抽象方法。
2. 接口**没有状态**（只有常量）、**没有构造方法**，这是它和抽象类最硬的区别。
3. 「是什么」用抽象类（单继承 + 有状态），「能做什么」用接口（多实现 + 无状态）。

**中级 —— 会设计：**

4. **面向接口编程**是地基：依赖抽象让代码可替换、可测试，这也是 Spring 依赖注入的理论基础。
5. `if-else` 分支超过三个且还会增加 → **策略模式**；需要在特定时机通知调用方 → **回调**。
6. 单抽象方法的接口自动获得 **Lambda** 支持，`Comparator` 那套流式 API 全靠 `default` 方法。
7. 接口**宁小勿大**（ISP），接口**归属调用方**而非实现方，**不要为只有一个实现的类加接口**。

**高级 —— 懂机制：**

8. `default` 方法冲突三规则：**类优先 > 最具体接口 > 显式重写**，复用用 `接口名.super.方法()`。
9. **JDK 动态代理只能代理接口**（`$ProxyN` 已 `extends Proxy`，单继承槽位被占），这是"按接口注入"的硬性理由，也是类内自调用导致 AOP 失效的根源。
10. 要**开放扩展**用普通接口 + SPI；要**封闭建模**换编译期穷尽检查，用**密封接口** + 模式匹配。
