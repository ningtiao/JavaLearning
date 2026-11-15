# Java 基础之抽象类

> 面向初学者的入门讲解。读完你应该能说清：抽象类解决什么问题、`abstract` 怎么用、哪些写法编译器不允许、什么时候该用抽象类而不是接口。

---

## 目录

1. [先看一个问题](#1-先看一个问题)
2. [abstract 关键字的两种用法](#2-abstract-关键字的两种用法)
3. [七条核心规则](#3-七条核心规则)
4. [abstract 不能和谁一起用](#4-abstract-不能和谁一起用)
5. [抽象类的构造方法](#5-抽象类的构造方法)
6. [抽象类 + 多态：真正的用途](#6-抽象类--多态真正的用途)
7. [模板方法模式：抽象类最经典的用法](#7-模板方法模式抽象类最经典的用法)
8. [抽象类 vs 接口](#8-抽象类-vs-接口)
9. [怎么选：抽象类还是接口](#9-怎么选抽象类还是接口)
10. [完整示例](#10-完整示例)
11. [常见误区](#11-常见误区)
12. [练习题](#12-练习题)

---

## 1. 先看一个问题

假设要写三种图形，都需要计算面积：

```java
class Circle {
    double radius;
    double area() { return Math.PI * radius * radius; }
}

class Rectangle {
    double width, height;
    double area() { return width * height; }
}

class Triangle {
    double base, height;
    double area() { return base * height / 2; }
}
```

三个类各自独立，于是出现两个麻烦：

**麻烦一：没法统一处理。** 想写个方法打印任意图形的面积，参数类型该写什么？

```java
// 只能为每种图形写一个重载，图形多了就爆炸
static void print(Circle c) { System.out.println(c.area()); }
static void print(Rectangle r) { System.out.println(r.area()); }
```

**麻烦二：没有约束力。** 新来的同事写了个 `Square` 类，把方法名写成了 `getArea()`，或者干脆忘了写。编译器不会提醒，因为没人规定"图形必须有 area() 方法"。

用继承提个父类出来，能解决第一个问题：

```java
class Shape {
    double area() {
        return 0;   // 这里返回什么？
    }
}
```

但 `Shape` 的 `area()` 没法写 —— "图形"这个概念本身没有面积公式，只有具体图形才有。返回 0 是硬凑的，而且子类如果忘了重写，就会悄悄得到一个错误的 0。

**抽象类就是为这种场景设计的**：把"所有图形都能算面积"这件事声明出来，但**不提供实现，强制子类自己实现**。

```java
abstract class Shape {
    abstract double area();   // 只声明，不实现，没有方法体
}
```

两个问题一起解决了：

- 参数类型写 `Shape`，一个方法处理所有图形。
- 子类不实现 `area()` 就**编译报错**，问题在写代码时就被拦住，而不是等到线上出 bug。

---

## 2. abstract 关键字的两种用法

`abstract` 可以修饰**方法**和**类**。

### 抽象方法

只有声明，没有方法体，**用分号结尾**，不写 `{}`：

```java
abstract double area();          // 正确
abstract double area() { }       // 错误：抽象方法不能有方法体
abstract double area() { return 0; }  // 错误：同上
```

它的含义是："我规定所有子类都必须有这个方法，但具体怎么实现由子类决定。"

### 抽象类

用 `abstract` 修饰类，表示这是一个**不完整的类**，不能直接创建对象：

```java
abstract class Shape {
    // ...
}

Shape s = new Shape();   // 编译错误：Shape is abstract; cannot be instantiated
```

### 抽象类里能放什么

这是初学者常误解的地方 —— 抽象类**不是只能放抽象方法**，普通类能有的东西它几乎都能有：

```java
abstract class Shape {
    // 1. 成员变量（可以有状态）
    protected String name;
    
    // 2. 静态常量
    public static final double PI = 3.14159;
    
    // 3. 构造方法（不能用来 new，但子类会调用它）
    public Shape(String name) {
        this.name = name;
    }
    
    // 4. 抽象方法：子类必须实现
    public abstract double area();
    
    // 5. 普通方法：子类直接继承，有现成实现
    public String getName() {
        return name;
    }
    
    // 6. 静态方法
    public static void hello() {
        System.out.println("我是图形类");
    }
    
    // 7. final 方法：子类不能重写
    public final void describe() {
        System.out.println(name + " 的面积是 " + area());
    }
}
```

**这正是抽象类比接口强的地方**：它既能定规矩（抽象方法），又能提供现成的、可复用的代码（普通方法和字段）。

---

## 3. 七条核心规则

| # | 规则 | 说明 |
|---|------|------|
| 1 | 抽象类**不能实例化** | `new Shape()` 编译报错 |
| 2 | 有抽象方法的类**必须**声明为抽象类 | 否则编译报错 |
| 3 | 抽象类**不一定**有抽象方法 | 可以一个抽象方法都没有 |
| 4 | 子类继承抽象类，**必须实现所有抽象方法** | 否则子类自己也得声明为 abstract |
| 5 | 抽象类**可以有构造方法** | 供子类通过 `super()` 调用 |
| 6 | 抽象类可以有普通方法、字段、静态方法、final 方法 | 见上一节 |
| 7 | 抽象类可以继承抽象类，也可以实现接口 | 继承体系照常工作 |

逐条看关键的几条。

### 规则 2 和 3：方向是单向的

```java
// 规则 2：有抽象方法，类就必须是抽象的
class Shape {              // 编译错误！Shape 不是 abstract 却有抽象方法
    abstract double area();
}

// 规则 3：抽象类可以没有抽象方法
abstract class Tool {      // 完全合法
    void run() {
        System.out.println("干活");
    }
}
```

规则 3 看着没用，其实有个实际场景：**你想让这个类只能被继承、不能被直接创建对象**（比如一个工具基类、一个配置基类），加个 `abstract` 就达到目的了。

### 规则 4：子类有两条路可走

```java
abstract class Shape {
    abstract double area();
    abstract double perimeter();
}

// 路 1：实现全部抽象方法 → 成为可实例化的"具体类"
class Circle extends Shape {
    double radius;
    Circle(double radius) { this.radius = radius; }
    
    @Override double area() { return Math.PI * radius * radius; }
    @Override double perimeter() { return 2 * Math.PI * radius; }
}

// 路 2：只实现一部分 → 自己必须也是抽象类，剩下的留给下一级
abstract class Polygon extends Shape {
    @Override double perimeter() { return 0; }   // 实现了这个
    // area() 没实现，所以 Polygon 必须是 abstract
}

class Square extends Polygon {
    double side;
    Square(double side) { this.side = side; }
    @Override double area() { return side * side; }   // 把最后一个补上
}
```

记住这个链条：**抽象方法会一级一级往下传，直到某个子类把它全部实现为止。**

---

## 4. abstract 不能和谁一起用

三个修饰符和 `abstract` **互相冲突**，一起写就是编译错误。理解原因比死记硬背有用。

| 组合 | 为什么不行 |
|------|-----------|
| `abstract` + `final` | `abstract` 的意思是"必须被子类实现/继承"，`final` 的意思是"禁止被继承/重写"。两者语义直接矛盾 |
| `abstract` + `private` | 抽象方法要让子类重写，但 `private` 的成员子类根本看不见，没法重写 |
| `abstract` + `static` | `static` 方法属于类，通过类名调用，不参与多态；抽象方法必须靠多态在运行时找到子类实现 |

```java
abstract class Demo {
    abstract final void a();     // 错误
    abstract private void b();   // 错误
    abstract static void c();    // 错误
    
    abstract protected void d(); // 正确
    abstract public void e();    // 正确
    abstract void f();           // 正确（包级私有）
}

final abstract class X { }       // 错误：类也不能同时 final 和 abstract
```

另外注意：**抽象方法不能是 `private`，但可以是 `protected`**。如果只想让子类用、不想对外暴露，用 `protected`。

---

## 5. 抽象类的构造方法

抽象类**可以有构造方法**，这点特别容易让初学者困惑："不能 new，构造方法给谁用？"

答案是：**给子类用。** 子类的构造方法里，`super(...)` 调用的就是它。

```java
abstract class Shape {
    protected String name;
    
    public Shape(String name) {
        this.name = name;
        System.out.println("Shape 的构造方法执行了");
    }
    
    public abstract double area();
}

class Circle extends Shape {
    private double radius;
    
    public Circle(String name, double radius) {
        super(name);          // 必须调用父类构造方法
        this.radius = radius;
        System.out.println("Circle 的构造方法执行了");
    }
    
    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}
```

执行 `new Circle("圆", 2)` 的输出顺序：

```
Shape 的构造方法执行了
Circle 的构造方法执行了
```

**先父类，后子类** —— 这和普通的继承完全一样，抽象类没有任何特殊之处。创建子类对象时，父类部分的初始化必须先完成。

> **要点**：如果抽象类只写了带参数的构造方法（没有无参构造），子类的构造方法里**必须**显式写 `super(...)`，否则编译器找不到无参的 `super()` 会报错。

### 那"看起来像 new 抽象类"的写法是什么？

你可能见过这种代码：

```java
Shape s = new Shape("临时图形") {
    @Override
    public double area() { return 0; }
};
```

这**不是**在实例化抽象类，而是**匿名内部类** —— Java 在后台悄悄创建了一个继承 `Shape` 的无名子类，并实现了 `area()`，`new` 出来的是那个子类的对象。规则"抽象类不能实例化"并没有被破坏。

---

## 6. 抽象类 + 多态：真正的用途

抽象类单独看没什么意义，它的价值在和**多态**配合时才体现出来。

```java
public class ShapeDemo {
    public static void main(String[] args) {
        // 父类类型的变量，指向子类对象 —— 这就是多态
        Shape[] shapes = {
            new Circle("圆", 2),
            new Square("正方形", 3),
            new Triangle("三角形", 4, 5)
        };
        
        double total = 0;
        for (Shape s : shapes) {
            // 编译时看到的是 Shape.area()
            // 运行时实际执行的是各个子类自己的 area()
            System.out.printf("%s 面积 = %.2f%n", s.getName(), s.area());
            total += s.area();
        }
        System.out.printf("总面积 = %.2f%n", total);
    }
}
```

注意这段循环里的三个关键点：

1. **数组类型是 `Shape[]`** —— 一种类型装下所有图形，这是抽象类带来的统一。
2. **调用的是 `s.area()`** —— 代码只认识 `Shape`，完全不知道具体是圆还是方。
3. **新增图形不用改这段代码** —— 写个 `Pentagon extends Shape`，丢进数组就能用。

这就是**面向抽象编程**：上层代码依赖抽象类（稳定），不依赖具体子类（易变）。新需求通过"加一个子类"满足，而不是"改一堆 if-else"。

### 对比一下不用抽象类的写法

```java
// 不用抽象类：每加一种图形，就得回来改这个方法
static double area(Object o) {
    if (o instanceof Circle c)      return Math.PI * c.radius * c.radius;
    else if (o instanceof Square s) return s.side * s.side;
    else if (o instanceof Triangle t) return t.base * t.height / 2;
    else throw new IllegalArgumentException("不认识的图形");
}
```

每来一种新图形，都要翻回来改这个 `if-else`，还可能漏改别的地方。抽象类版本一行都不用动。

---

## 7. 模板方法模式：抽象类最经典的用法

这是抽象类**最有价值、也最能体现它和接口区别**的场景。

**思路**：父类把"做事的固定步骤"定下来，其中个别步骤自己不实现，留给子类填。

举例：泡饮料。不管泡咖啡还是泡茶，流程都是"烧水 → 冲泡 → 倒杯 → 加调料"，其中"烧水"和"倒杯"两步完全一样，"冲泡"和"加调料"因饮料而异。

```java
abstract class Beverage {
    
    // 模板方法：定义固定流程，用 final 防止子类改流程
    public final void prepare() {
        boilWater();
        brew();              // 交给子类
        pourInCup();
        if (customerWantsCondiments()) {   // 钩子方法，子类可选择性覆盖
            addCondiments(); // 交给子类
        }
    }
    
    // 公共步骤：父类直接实现，所有子类复用
    private void boilWater() {
        System.out.println("烧水");
    }
    
    private void pourInCup() {
        System.out.println("倒进杯子");
    }
    
    // 抽象步骤：子类必须实现
    protected abstract void brew();
    protected abstract void addCondiments();
    
    // 钩子方法：有默认实现，子类想改就改
    protected boolean customerWantsCondiments() {
        return true;
    }
}

class Coffee extends Beverage {
    @Override protected void brew() {
        System.out.println("用沸水冲泡咖啡粉");
    }
    @Override protected void addCondiments() {
        System.out.println("加糖和牛奶");
    }
}

class Tea extends Beverage {
    @Override protected void brew() {
        System.out.println("用沸水浸泡茶叶");
    }
    @Override protected void addCondiments() {
        System.out.println("加柠檬");
    }
    @Override protected boolean customerWantsCondiments() {
        return false;   // 这位客人要清茶
    }
}
```

调用：

```java
new Coffee().prepare();
// 烧水 / 用沸水冲泡咖啡粉 / 倒进杯子 / 加糖和牛奶

new Tea().prepare();
// 烧水 / 用沸水浸泡茶叶 / 倒进杯子     ← 跳过了加调料
```

三个设计要点值得记住：

| 做法 | 目的 |
|------|------|
| `prepare()` 用 `final` | **锁定流程**，子类只能填空，不能打乱步骤顺序 |
| `boilWater()` 用 `private` | 公共实现对外隐藏，子类直接复用，不用关心细节 |
| `customerWantsCondiments()` 给默认实现 | 这叫**钩子方法**，子类不管就用默认行为，想定制就重写 |

**这套写法接口做不到** —— 接口没有字段、没有构造方法、方法默认都是 `public`（无法 `private` 隐藏实现细节，Java 9 后虽有 private 接口方法但不能被继承复用），也无法用 `final` 锁死流程不让子类重写。

---

## 8. 抽象类 vs 接口

Java 8 给接口加了 `default` 方法后，两者的界限模糊了不少，但核心差异依然存在。

| 对比项 | 抽象类 `abstract class` | 接口 `interface` |
|--------|------------------------|-----------------|
| **继承/实现数量** | 单继承，一个类只能 `extends` 一个 | 多实现，可以 `implements` 多个 |
| **成员变量** | 任意字段，可以有**可变状态** | 只能是 `public static final` 常量 |
| **构造方法** | 有 | **没有** |
| **方法实现** | 抽象方法 + 普通方法任意混搭 | Java 8+ 可用 `default` / `static` 提供实现 |
| **访问修饰符** | `public` / `protected` / 默认 / `private` 都行 | 方法默认 `public`，不能 `protected` |
| **final 方法** | 可以，用来锁定流程 | **不行**，`default` 方法无法声明 final |
| **语义** | **is-a**（是什么）：Dog **是一种** Animal | **can-do**（能做什么）：Dog **能够** 游泳 |

### 关键区别一：状态

抽象类能有可变的成员变量，接口不能。

```java
abstract class Animal {
    protected String name;    // 可变状态，子类共享
    protected int age;
}

interface Swimmable {
    int MAX_DEPTH = 100;      // 隐含 public static final，改不了
    // String name;           // 编译错误：接口字段必须初始化且是常量
}
```

### 关键区别二：数量

一个类只能继承一个抽象类，但能实现多个接口。这决定了设计思路：

```java
// Dog "是一种" Animal —— 只能有一个这样的身份，用抽象类
abstract class Animal { }

// Dog "能够" 游泳、"能够" 奔跑 —— 能力可以有很多，用接口
interface Swimmable { void swim(); }
interface Runnable2 { void run(); }

class Dog extends Animal implements Swimmable, Runnable2 {
    @Override public void swim() { System.out.println("狗刨"); }
    @Override public void run()  { System.out.println("四条腿跑"); }
}
```

### 关键区别三：扩展成本

往抽象类里加一个**普通方法**，所有子类自动获得，不用改代码。往接口里加一个**抽象方法**，所有实现类全部编译报错（这是 Java 8 引入 `default` 方法的根本原因 —— 为了给 `Collection` 这类老接口加 `stream()` 而不破坏现有代码）。

---

## 9. 怎么选：抽象类还是接口

照这三个问题问自己，答案就出来了：

**问题 1：子类之间有没有要共享的代码或字段？**
有 → 抽象类。没有，只需要定一组方法签名 → 接口。

**问题 2：这是"是什么"还是"能做什么"？**
`Cat` **是一种** `Animal` → 抽象类。`Cat` **能够** `Serializable`（被序列化）→ 接口。

**问题 3：实现类需不需要同时拥有多个身份？**
需要 → 必须用接口（单继承限制）。

### 实际项目里的典型组合

成熟的做法是**两个一起用**：接口定契约，抽象类提供骨架实现。

```java
// 接口：定义能力契约，供外部依赖
interface Repository<T> {
    void save(T entity);
    T findById(Long id);
    void delete(Long id);
}

// 抽象类：提供公共骨架，减少子类重复代码
abstract class AbstractRepository<T> implements Repository<T> {
    
    protected final Map<Long, T> storage = new HashMap<>();  // 共享状态
    
    @Override
    public T findById(Long id) {     // 公共实现，所有子类复用
        return storage.get(id);
    }
    
    @Override
    public void delete(Long id) {    // 公共实现
        storage.remove(id);
    }
    
    // save 不实现，因为每种实体的 id 获取方式不同，留给子类
}

class UserRepository extends AbstractRepository<User> {
    @Override
    public void save(User user) {
        storage.put(user.getId(), user);
    }
}
```

JDK 里到处是这种模式：`Collection`（接口）→ `AbstractCollection`（抽象类）→ `ArrayList`（具体类）。`List` / `AbstractList` / `Map` / `AbstractMap` 都是同样的三层结构。看到 `Abstract` 开头的 JDK 类名，基本都是这个套路。

---

## 10. 完整示例

把前面的知识串成一个能直接运行的程序：

```java
import java.util.ArrayList;
import java.util.List;

// ========== 抽象类 ==========
abstract class Employee {
    // 1. 共享字段
    protected String name;
    protected int id;

    // 2. 构造方法：供子类 super() 调用
    public Employee(String name, int id) {
        this.name = name;
        this.id = id;
    }

    // 3. 抽象方法：每种员工的薪资算法不同，必须由子类实现
    public abstract double calculateSalary();

    // 4. 普通方法：所有子类共享，直接继承
    public String getName() {
        return name;
    }

    // 5. 钩子方法：有默认实现，子类可按需重写
    protected double getBonus() {
        return 0;
    }

    // 6. 模板方法：用 final 锁定"打印工资单"的固定流程
    public final void printPayslip() {
        System.out.println("=============================");
        System.out.println("工号：" + id + "    姓名：" + name);
        System.out.printf("基本薪资：%.2f%n", calculateSalary());
        System.out.printf("奖金：%.2f%n", getBonus());
        System.out.printf("实发合计：%.2f%n", calculateSalary() + getBonus());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name='" + name + "', id=" + id + "}";
    }
}

// ========== 子类 1：月薪制 ==========
class FullTimeEmployee extends Employee {
    private double monthlySalary;

    public FullTimeEmployee(String name, int id, double monthlySalary) {
        super(name, id);
        this.monthlySalary = monthlySalary;
    }

    @Override
    public double calculateSalary() {
        return monthlySalary;
    }

    @Override
    protected double getBonus() {     // 重写钩子方法：全职有年终奖
        return monthlySalary * 0.2;
    }
}

// ========== 子类 2：时薪制 ==========
class PartTimeEmployee extends Employee {
    private double hourlyRate;
    private int hours;

    public PartTimeEmployee(String name, int id, double hourlyRate, int hours) {
        super(name, id);
        this.hourlyRate = hourlyRate;
        this.hours = hours;
    }

    @Override
    public double calculateSalary() {
        return hourlyRate * hours;
    }
    // 不重写 getBonus()，用父类默认的 0
}

// ========== 测试 ==========
public class EmployeeDemo {
    public static void main(String[] args) {
        // 多态：用父类类型统一管理所有子类对象
        List<Employee> staff = new ArrayList<>();
        staff.add(new FullTimeEmployee("张三", 1001, 12000));
        staff.add(new PartTimeEmployee("李四", 1002, 80, 100));

        double total = 0;
        for (Employee e : staff) {
            e.printPayslip();                              // 流程固定
            total += e.calculateSalary() + e.getBonus();   // 算法由子类决定
        }

        System.out.println("=============================");
        System.out.printf("本月人力成本合计：%.2f%n", total);

        // Employee e = new Employee("王五", 1003);  // 编译错误：抽象类不能实例化
    }
}
```

实际运行输出：

```
=============================
工号：1001    姓名：张三
基本薪资：12000.00
奖金：2400.00
实发合计：14400.00
=============================
工号：1002    姓名：李四
基本薪资：8000.00
奖金：0.00
实发合计：8000.00
=============================
本月人力成本合计：22400.00
```

新增一种员工类型（比如"实习生"），只需要加一个 `extends Employee` 的类，`main` 里的循环一行都不用改。

---

## 11. 常见误区

| 误区 | 正确理解 |
|------|----------|
| 抽象类里只能写抽象方法 | 普通方法、字段、构造方法、静态方法、final 方法都能写 |
| 抽象类必须有抽象方法 | 不是必须。没有抽象方法的抽象类也合法，作用是"禁止被直接实例化" |
| 抽象类不能有构造方法 | 可以有，而且很常用，供子类 `super()` 调用 |
| 抽象方法可以写 `{}` 空方法体 | 不行。必须用分号结尾，写了 `{}` 就不是抽象方法了 |
| 抽象方法可以是 private | 不行。子类看不见就无法重写，和 abstract 矛盾 |
| `new Shape(){...}` 说明抽象类能实例化 | 那是匿名内部类，Java 创建了一个无名子类，new 的是子类 |
| 子类必须实现全部抽象方法 | 不一定。子类也声明为 `abstract` 就可以只实现一部分 |
| 抽象类和接口功能一样，随便选 | 抽象类能有可变状态和构造方法且单继承；接口无状态但可多实现 |
| 抽象类不能有 main 方法 | 可以有，而且能正常运行（`main` 是 static，不需要实例） |

### 一个高频坑：忘了 `@Override`

```java
abstract class Shape {
    abstract double area();
}

class Circle extends Shape {
    double radius;
    // 想重写 area()，但不小心写成了 Area()
    double Area() { return Math.PI * radius * radius; }
}
// 编译错误：Circle is not abstract and does not override abstract method area()
```

抽象方法这种情况下编译器会帮你报错，还算幸运。但如果是重写**普通方法**写错了名字，编译器不会报错，它只会被当成一个新方法 —— 所以**重写一律加 `@Override`**。

### 另一个坑：在构造方法里调用抽象方法

```java
abstract class Base {
    public Base() {
        init();        // 危险！
    }
    protected abstract void init();
}

class Child extends Base {
    private String data = "初始值";
    
    @Override
    protected void init() {
        System.out.println(data);   // 输出 null，不是"初始值"！
    }
}
```

原因是执行顺序：`Child` 的构造方法先调 `super()` → `Base` 构造方法调 `init()` → 此时 `Child` 的字段**还没开始初始化**，`data` 还是默认值 `null`。

**结论：不要在构造方法里调用可被子类重写的方法。** 需要初始化就提供一个单独的 `init()` 方法，让调用方在对象构造完成后显式调用。

---

## 12. 练习题

1. 写一个抽象类 `Animal`，包含 `name` 字段、带参构造方法、抽象方法 `makeSound()`，以及普通方法 `sleep()`（打印"xxx 在睡觉"）。再写 `Dog`、`Cat` 两个子类实现它。在 `main` 里用 `Animal[]` 数组遍历调用，体会多态。

2. 判断下面每段代码能否编译通过，不能的说明原因：

```java
// A
abstract class A { }

// B
abstract class B { void run() { } }

// C
class C { abstract void run(); }

// D
abstract class D { abstract static void run(); }

// E
abstract class E { abstract void run(); }
class F extends E { }

// G
abstract class G { abstract void run(); }
abstract class H extends G { }
```

3. 用模板方法模式写一个抽象类 `DataExporter`，流程固定为「连接数据源 → 读取数据 → **格式化** → 写入文件 → 关闭连接」，其中只有「格式化」一步是抽象的。然后写 `CsvExporter` 和 `JsonExporter` 两个子类。要求：模板方法用 `final` 修饰。

4. 给第 3 题加一个钩子方法 `needsHeader()`，默认返回 `true`（输出表头）。让 `JsonExporter` 重写它返回 `false`。

5. 思考题：JDK 里的 `AbstractList` 为什么被设计成抽象类，而不是直接让 `ArrayList` 实现 `List` 接口？提示：数一数 `List` 接口有多少个方法。

6. 思考题：下面的代码输出什么？为什么？

```java
abstract class Parent {
    Parent() {
        System.out.println("Parent 构造");
        show();
    }
    abstract void show();
}

class Child extends Parent {
    int num = 100;
    Child() {
        System.out.println("Child 构造");
    }
    @Override void show() {
        System.out.println("num = " + num);
    }
}

public class Test {
    public static void main(String[] args) {
        new Child();
    }
}
```

<details>
<summary>第 2 题参考答案</summary>

| 代码 | 能否编译 | 原因 |
|------|---------|------|
| A | ✅ 能 | 抽象类不要求有抽象方法 |
| B | ✅ 能 | 抽象类可以只有普通方法 |
| C | ❌ 不能 | 有抽象方法的类必须声明为 `abstract` |
| D | ❌ 不能 | `abstract` 和 `static` 冲突，静态方法不参与多态 |
| E/F | ❌ 不能 | `F` 是具体类却没实现 `run()`，必须实现或自己声明为 abstract |
| G/H | ✅ 能 | `H` 也是抽象类，可以把 `run()` 继续往下传 |

</details>

<details>
<summary>第 6 题参考答案</summary>

输出：

```
Parent 构造
num = 0
Child 构造
```

执行顺序是：
1. `new Child()` → 隐式调用 `super()`，进入 `Parent` 构造方法
2. 打印 `Parent 构造`
3. 调用 `show()` —— 多态生效，执行的是 `Child.show()`
4. 但此时 `Child` 的字段**还没初始化**，`num` 是 `int` 的默认值 `0`，不是 `100`
5. `Parent` 构造方法结束，才开始初始化 `Child` 的字段（`num = 100`）
6. 最后执行 `Child` 构造方法体，打印 `Child 构造`

这就是第 11 节讲的"不要在构造方法里调用可被重写的方法"。完整的初始化顺序是：**父类字段 → 父类构造方法体 → 子类字段 → 子类构造方法体**。

</details>

---

## 一句话总结

抽象类是一个**写了一半的父类**：它用抽象方法规定"子类必须做什么"，用普通方法和字段提供"大家都能用的现成代码"。

初学者记住三件事就够了：

1. **抽象类不能 `new`**，必须靠子类实例化。
2. **继承它的具体类必须实现所有抽象方法**，否则自己也得是抽象类。
3. **"是什么"用抽象类（单继承 + 有状态），"能做什么"用接口（多实现 + 无状态）**；两者结合（接口定契约 + 抽象类给骨架）是实际项目里最常见的做法。
