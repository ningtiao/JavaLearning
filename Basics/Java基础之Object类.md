// 2026-11-15

# Java 基础之 Object 类

> 面向初学者的入门讲解。读完你应该能说清：Object 是什么、它有哪些方法、哪些方法必须自己重写、怎么重写才不出错。

---

## 目录

1. [Object 是什么](#1-object-是什么)
2. [Object 的 11 个方法总览](#2-object-的-11-个方法总览)
3. [toString()：让对象能被看懂](#3-tostring让对象能被看懂)
4. [equals()：判断"内容是否相同"](#4-equals判断内容是否相同)
5. [hashCode()：和 equals 必须成对出现](#5-hashcode和-equals-必须成对出现)
6. [getClass()：拿到对象的"身份证"](#6-getclass拿到对象的身份证)
7. [clone()：对象复制](#7-clone对象复制)
8. [finalize()：已废弃，不要用](#8-finalize已废弃不要用)
9. [wait() / notify() / notifyAll()：线程通信](#9-wait--notify--notifyall线程通信)
10. [完整示例：一个规范的类](#10-完整示例一个规范的类)
11. [常见误区](#11-常见误区)
12. [练习题](#12-练习题)

---

## 1. Object 是什么

`java.lang.Object` 是 Java 所有类的**根父类**（超类）。

两条规则：

- 你写的每个类，如果没有写 `extends`，编译器会自动让它继承 `Object`。
- 如果写了 `extends Father`，而 `Father` 又没写 `extends`，那么 `Father` 继承 `Object`。**继承链的最顶端一定是 Object。**

```java
// 这两种写法完全等价
class Student { }
class Student extends Object { }
```

**为什么这很重要？**

因为"所有类都是 Object 的子类"，所以 `Object` 类型的变量可以装任何对象，任何对象也都天生拥有 Object 里定义的方法。

```java
Object o1 = new Student();      // 可以
Object o2 = "hello";            // 可以，String 也继承 Object
Object o3 = new int[]{1, 2, 3}; // 可以，数组也是对象
Object o4 = 100;                // 可以，自动装箱成 Integer
```

> **注意**：基本数据类型（`int`、`double`、`char`、`boolean` 等）**不是**对象，不继承 Object。能赋值给 Object 是因为 Java 做了自动装箱（`int` → `Integer`）。

这也是 `ArrayList` 在泛型出现之前能存放任意类型的原因——它内部存的就是 `Object[]`。

---

## 2. Object 的 11 个方法总览

| 方法 | 作用 | 初学者要掌握到什么程度 |
|------|------|------------------------|
| `toString()` | 返回对象的字符串描述 | **必须会重写** |
| `equals(Object obj)` | 比较两个对象是否"相等" | **必须会重写** |
| `hashCode()` | 返回对象的哈希码 | **必须会重写**（和 equals 一起） |
| `getClass()` | 返回对象的运行时类型 | 会用就行，不能重写（final） |
| `clone()` | 复制一个对象 | 了解概念，知道深浅拷贝区别 |
| `finalize()` | 对象被回收前调用 | 知道它已废弃，**不要用** |
| `wait()` | 让当前线程等待 | 先了解，学多线程时再深入 |
| `wait(long timeout)` | 等待指定毫秒 | 同上 |
| `wait(long timeout, int nanos)` | 等待指定时间（更精确） | 同上 |
| `notify()` | 唤醒一个等待的线程 | 同上 |
| `notifyAll()` | 唤醒所有等待的线程 | 同上 |

其中 `getClass()`、`notify()`、`notifyAll()`、`wait()` 都是 `final` 的，**不能重写**。

可以重写的只有三个常用的：`toString()`、`equals()`、`hashCode()`（加上 `clone()` 和已废弃的 `finalize()`）。

---

## 3. toString()：让对象能被看懂

### 默认行为

Object 的 `toString()` 返回的是：`全限定类名@哈希码的十六进制`。

```java
class Student {
    String name = "张三";
    int age = 18;
}

public class Demo {
    public static void main(String[] args) {
        Student s = new Student();
        System.out.println(s.toString()); // Student@1b6d3586
        System.out.println(s);            // Student@1b6d3586（println 会自动调 toString）
    }
}
```

`Student@1b6d3586` 对人类来说毫无意义——看不出姓名和年龄。

### 重写后

```java
class Student {
    String name = "张三";
    int age = 18;

    @Override
    public String toString() {
        return "Student{name='" + name + "', age=" + age + "}";
    }
}

// 输出：Student{name='张三', age=18}
```

### 什么时候会自动调用 toString()

这三种情况 Java 会帮你调：

```java
Student s = new Student();

System.out.println(s);              // 1. 打印对象
String str = "学生：" + s;           // 2. 和字符串做 + 拼接
String msg = String.valueOf(s);     // 3. String.valueOf()
```

### 实用建议

- IDE 可以自动生成：IntelliJ IDEA 里 `Alt + Insert` → `toString()`。
- 调试时非常依赖它，**自定义的实体类建议都重写**。
- **不要把密码、密钥等敏感字段放进 toString()**，因为日志里会打出来。

---

## 4. equals()：判断"内容是否相同"

这是初学者最容易踩坑的地方。

### 先搞清楚 `==` 和 `equals()` 的区别

| 比较方式 | 比较基本类型 | 比较引用类型 |
|----------|--------------|--------------|
| `==` | 比较**值** | 比较**内存地址**（是否同一个对象） |
| `equals()` | 用不了（基本类型没有方法） | 默认也是比地址，**重写后比内容** |

### Object 的 equals() 源码长这样

```java
public boolean equals(Object obj) {
    return (this == obj);   // 就是 == ，比地址
}
```

所以**不重写 equals()，它和 `==` 完全一样**。

```java
class Student {
    String name;
    int age;
    Student(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

Student s1 = new Student("张三", 18);
Student s2 = new Student("张三", 18);

System.out.println(s1 == s2);      // false，两个不同的对象
System.out.println(s1.equals(s2)); // false！没重写，等于比地址
```

姓名年龄都一样，业务上应该算同一个学生，但结果是 `false`。要让它变 `true`，必须重写。

### 为什么 String 的 equals() 能比内容？

因为 **String 类已经帮你重写好了**。同理 `Integer`、`Double`、`ArrayList`、`HashMap` 这些 JDK 类都重写过。

```java
String a = new String("abc");
String b = new String("abc");
System.out.println(a == b);      // false，两个对象
System.out.println(a.equals(b)); // true，String 重写了 equals
```

### 重写 equals() 的标准写法

```java
@Override
public boolean equals(Object obj) {
    // 1. 是同一个对象，直接返回 true（提升效率）
    if (this == obj) return true;

    // 2. 传进来是 null，或者类型不同，返回 false
    if (obj == null || getClass() != obj.getClass()) return false;

    // 3. 向下转型，比较各个字段
    Student other = (Student) obj;
    return this.age == other.age
        && Objects.equals(this.name, other.name); // 字符串用 Objects.equals 防 NPE
}
```

记住这个顺序：**自反判断 → 判空与类型判断 → 转型 → 逐字段比较**。

> `Objects.equals(a, b)` 是 `java.util.Objects` 的工具方法，内部逻辑是 `a == b || (a != null && a.equals(b))`，能自动处理 null，比直接写 `this.name.equals(other.name)` 安全。

### equals() 必须满足的五条约定

| 约定 | 含义 |
|------|------|
| 自反性 | `x.equals(x)` 必须是 `true` |
| 对称性 | `x.equals(y)` 为 true，则 `y.equals(x)` 也必须为 true |
| 传递性 | x 等于 y，y 等于 z，则 x 必须等于 z |
| 一致性 | 对象没改变时，多次调用结果必须一致 |
| 非空性 | `x.equals(null)` 必须是 `false`，不能抛异常 |

初学者按上面的标准模板写，这五条自然就满足了。

---

## 5. hashCode()：和 equals 必须成对出现

### hashCode 是什么

`hashCode()` 返回一个 `int`，代表对象的"哈希码"。你可以把它理解成**对象在哈希表里的"门牌号"**，`HashMap`、`HashSet`、`Hashtable` 全靠它快速定位元素。

默认实现返回的是和对象内存地址相关的一个数值，所以**两个内容相同的新对象，默认哈希码不同**。

### 核心规则（面试必问）

1. **两个对象 equals 为 true，它们的 hashCode 必须相同。**
2. 两个对象 hashCode 相同，equals 不一定为 true（这叫**哈希冲突**，允许存在）。
3. 所以：**重写了 equals()，就必须重写 hashCode()**。

### 不遵守会出什么事

```java
// 假设 Student 只重写了 equals，没重写 hashCode
Set<Student> set = new HashSet<>();
set.add(new Student("张三", 18));
set.add(new Student("张三", 18));

System.out.println(set.size()); // 2 ！本该去重，结果存了两个
```

原因：`HashSet` 先用 `hashCode()` 找"桶"。两个对象哈希码不同，被分到不同的桶里，`equals()` 根本没机会被调用，于是重复元素就都存进去了。

同样的问题会出现在 `HashMap` 的 key 上：你用一个内容相同的新对象去 `get()`，会拿到 `null`。

### 重写 hashCode() 的标准写法

最简单可靠的方式，用 `Objects.hash()`：

```java
@Override
public int hashCode() {
    return Objects.hash(name, age);  // 把参与 equals 比较的字段都传进来
}
```

**关键点：`equals()` 里比较了哪些字段，`hashCode()` 就用哪些字段。** 两者必须基于同一组字段，否则规则就被破坏了。

---

## 6. getClass()：拿到对象的"身份证"

返回对象**运行时**的真实类型，是一个 `Class` 对象。它是 `final` 的，不能重写。

```java
Student s = new Student("张三", 18);

System.out.println(s.getClass());             // class com.example.Student
System.out.println(s.getClass().getName());      // com.example.Student（全限定名）
System.out.println(s.getClass().getSimpleName()); // Student（简单类名）
```

注意"运行时类型"的含义：

```java
Object obj = new Student("张三", 18);  // 编译时类型是 Object
System.out.println(obj.getClass().getSimpleName()); // Student（运行时类型）
```

### 和 `instanceof` 的区别

```java
class Person { }
class Student extends Person { }

Student s = new Student();

System.out.println(s instanceof Person);              // true，子类也算
System.out.println(s.getClass() == Person.class);     // false，必须类型完全一致
System.out.println(s.getClass() == Student.class);    // true
```

| 场景 | 推荐用法 |
|------|----------|
| 要求类型**完全一致** | `getClass() != obj.getClass()` |
| 允许子类也成立 | `obj instanceof Person` |

`getClass()` 还是 Java **反射**的入口，这部分等基础扎实了再学。

---

## 7. clone()：对象复制

`clone()` 用来复制一个对象。用它有两个前提：

1. 类必须实现 `Cloneable` 接口（一个空的标记接口，不实现会抛 `CloneNotSupportedException`）。
2. 重写 `clone()` 方法，把访问权限从 `protected` 放大到 `public`。

```java
class Student implements Cloneable {
    String name;
    int[] scores;

    @Override
    public Student clone() throws CloneNotSupportedException {
        return (Student) super.clone();
    }
}
```

### 浅拷贝 vs 深拷贝

`Object.clone()` 做的是**浅拷贝**：只复制字段的值。基本类型复制的是数值；引用类型复制的是**地址**，于是原对象和副本指向同一块数据。

```java
Student s1 = new Student();
s1.scores = new int[]{90, 85};

Student s2 = s1.clone();
s2.scores[0] = 60;

System.out.println(s1.scores[0]); // 60！s1 被连带改了
```

**深拷贝**需要手动把引用类型的成员也复制一份：

```java
@Override
public Student clone() throws CloneNotSupportedException {
    Student copy = (Student) super.clone();
    copy.scores = this.scores.clone();  // 数组也复制一份
    return copy;
}
```

> **实践建议**：`clone()` 的设计一直被诟病（需要实现空接口、要处理受检异常、深浅拷贝容易出错）。实际开发更常用**拷贝构造方法**或静态工厂方法：
>
> ```java
> public Student(Student other) {
>     this.name = other.name;
>     this.scores = other.scores.clone();
> }
> ```

---

## 8. finalize()：已废弃，不要用

`finalize()` 原本的设计是：对象被垃圾回收器回收之前，JVM 会调用它一次，给你一个释放资源的机会。

**但它已经在 Java 9 中被标记为 `@Deprecated`，并在后续版本中逐步移除。** 原因：

- **调用时机完全不确定**，甚至可能永远不被调用（程序退出前对象没被回收就不会调）。
- 会**拖慢垃圾回收**，还可能导致对象"复活"。
- 里面抛出的异常会被直接忽略，问题被静默吞掉。

**替代方案**：需要释放资源（文件、数据库连接、网络连接）就用 `try-with-resources`：

```java
try (FileInputStream fis = new FileInputStream("a.txt")) {
    // 使用 fis
} // 自动关闭，无论是否抛异常
```

让类实现 `AutoCloseable` 接口即可享受这个语法。

初学者对 `finalize()` 只需记住一句话：**知道它存在，永远不要用它。**

---

## 9. wait() / notify() / notifyAll()：线程通信

这四个方法（`wait` 有三个重载）用于**多线程之间的协作**，是 Java 早期的线程通信机制。

| 方法 | 作用 |
|------|------|
| `wait()` | 让当前线程进入等待，并**释放对象锁** |
| `wait(long timeout)` | 等待指定毫秒数后自动醒来 |
| `notify()` | 唤醒在该对象上等待的**一个**线程 |
| `notifyAll()` | 唤醒在该对象上等待的**所有**线程 |

两条硬性要求：

1. 必须在 `synchronized` 代码块或方法内调用，否则抛 `IllegalMonitorStateException`。
2. `wait()` 要放在 `while` 循环里检查条件，不能用 `if`（防止"虚假唤醒"）。

```java
synchronized (lock) {
    while (条件不满足) {
        lock.wait();       // 释放锁，进入等待
    }
    // 执行业务逻辑
    lock.notifyAll();      // 唤醒其他等待的线程
}
```

### 和 Thread.sleep() 的区别

| | `wait()` | `Thread.sleep()` |
|---|---|---|
| 定义位置 | `Object` 类 | `Thread` 类 |
| 是否释放锁 | **释放** | **不释放** |
| 如何醒来 | 被 notify 唤醒或超时 | 睡够时间自动醒 |
| 调用限制 | 必须在 synchronized 内 | 任何地方都能调 |

这部分属于多线程内容，**初学阶段先知道"这几个方法在 Object 里、和线程有关"就够了**，学到并发编程时再深入。

---

## 10. 完整示例：一个规范的类

把前面的知识串起来——这是一个重写了三大方法的标准实体类：

```java
import java.util.Objects;

public class Student {
    private String name;
    private int age;

    public Student(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }

    @Override
    public String toString() {
        return "Student{name='" + name + "', age=" + age + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Student other = (Student) obj;
        return age == other.age && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);   // 和 equals 用同一组字段
    }
}
```

验证一下：

```java
import java.util.HashSet;
import java.util.Set;

public class Demo {
    public static void main(String[] args) {
        Student s1 = new Student("张三", 18);
        Student s2 = new Student("张三", 18);

        System.out.println(s1);                  // Student{name='张三', age=18}
        System.out.println(s1 == s2);             // false，不是同一个对象
        System.out.println(s1.equals(s2));        // true，内容相同
        System.out.println(s1.hashCode() == s2.hashCode()); // true

        Set<Student> set = new HashSet<>();
        set.add(s1);
        set.add(s2);
        System.out.println(set.size());           // 1，成功去重
    }
}
```

> **提示**：Java 14 之后，如果你的类只是用来存数据，可以用 **record** 一行搞定，编译器会自动生成 `toString()`、`equals()`、`hashCode()`：
>
> ```java
> public record Student(String name, int age) { }
> ```

---

## 11. 常见误区

| 误区 | 正确理解 |
|------|----------|
| 用 `==` 比较两个字符串内容 | 比较内容要用 `equals()`，`==` 比的是地址 |
| 只重写 `equals()`，不管 `hashCode()` | 必须成对重写，否则 HashSet/HashMap 行为异常 |
| 重写时写成 `equals(Student s)` | 参数类型必须是 `Object`，否则是**重载**不是**重写**，加 `@Override` 能让编译器帮你发现 |
| `hashCode()` 返回的就是内存地址 | 是和地址**相关**的一个值，不等于地址；重写后更与地址无关 |
| 认为 `clone()` 一定是深拷贝 | `Object.clone()` 是**浅拷贝**，深拷贝要自己实现 |
| 指望 `finalize()` 释放资源 | 它已废弃，用 `try-with-resources` |
| 以为 `int` 也继承 Object | 基本类型不是对象，是自动装箱后才能当对象用 |
| `equals()` 里直接 `obj.equals(...)` 不判空 | 必须先判 `null` 和类型，否则抛 `NullPointerException` / `ClassCastException` |

### 一个高频坑：重写变重载

```java
// 错误：这不是重写 Object.equals，而是新增了一个重载方法
public boolean equals(Student other) { ... }

// 正确
@Override
public boolean equals(Object obj) { ... }
```

**永远加上 `@Override` 注解**。写错了编译器会直接报错，而不是让你在运行时 debug 半天。

---

## 12. 练习题

1. 写一个 `Book` 类，包含 `isbn`（String）、`title`（String）、`price`（double）三个字段，重写 `toString()`、`equals()`、`hashCode()`。规则：**ISBN 相同就算同一本书**（所以 equals 和 hashCode 只用 isbn）。

2. 用上面的 `Book` 创建两个 ISBN 相同但书名不同的对象，放进 `HashSet`，验证 `size()` 是 1。

3. 把 `Book` 的 `hashCode()` 改成 `return 1;`（所有对象哈希码都一样）。程序还能正确去重吗？为什么？性能上有什么问题？

4. 写一个 `Teacher` 类，里面有一个 `ArrayList<String> courses` 字段。实现 `Cloneable`，分别写出浅拷贝和深拷贝两个版本，用代码验证区别。

5. 解释：为什么 `getClass()` 被设计成 `final`（不允许重写）？如果能重写，`equals()` 的类型判断会出什么问题？

<details>
<summary>第 3 题参考答案</summary>

**还能正确去重**。因为 `HashSet` 的判断逻辑是"先比 hashCode 定位桶，桶内再用 equals 逐个比较"。哈希码全相同只意味着所有元素挤在同一个桶里，`equals()` 仍会被调用，所以去重结果是对的。

**但性能退化严重**：哈希表本该是 O(1) 查找，现在变成了在一条链表（元素多时 JDK 8+ 会转成红黑树）里线性/对数查找，等于白白浪费了哈希表的优势。

**结论**：返回固定值"合法但很糟"——这也说明了规则 2（hashCode 相同不代表 equals 相同）的存在意义。

</details>

---

## 一句话总结

Object 是所有类的祖先。初学者真正需要动手的只有三件事：**重写 `toString()` 方便调试，重写 `equals()` 定义什么叫"相等"，重写 `hashCode()` 让集合类正常工作**。记住最后两个必须成对出现，就避开了最常见的坑。
