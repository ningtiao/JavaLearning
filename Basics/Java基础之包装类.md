# Java 基础之包装类

> 面向初学者的入门讲解。读完你应该能说清：包装类为什么存在、装箱拆箱是怎么回事、为什么 `Integer` 用 `==` 比较会出诡异结果、哪些写法会悄悄抛 `NullPointerException`。
>
> 本文所有代码输出均在 **JDK 21** 上实测，不是推测值。

---

## 目录

1. [为什么需要包装类](#1-为什么需要包装类)
2. [八种包装类对照表](#2-八种包装类对照表)
3. [三个共同特征](#3-三个共同特征)
4. [装箱与拆箱](#4-装箱与拆箱)
5. [最大的坑：整数缓存池与 ==](#5-最大的坑整数缓存池与-)
6. [第二大坑：自动拆箱引发的 NPE](#6-第二大坑自动拆箱引发的-npe)
7. [第三个坑：equals 不做类型转换](#7-第三个坑equals-不做类型转换)
8. [字符串与基本类型的互相转换](#8-字符串与基本类型的互相转换)
9. [常用方法与常量速查](#9-常用方法与常量速查)
10. [浮点包装类的特殊情况](#10-浮点包装类的特殊情况)
11. [性能：别在循环里装箱](#11-性能别在循环里装箱)
12. [完整示例](#12-完整示例)
13. [常见误区](#13-常见误区)
14. [练习题](#14-练习题)

---

## 1. 为什么需要包装类

Java 有 8 种基本数据类型（`int`、`double`、`char` 等），它们**不是对象**，不继承 `Object`，没有方法可调用。这在三个地方会卡住。

### 卡点一：集合和泛型只能装对象

```java
List<int> list = new ArrayList<>();   // 编译错误！泛型参数必须是引用类型
List<Integer> list = new ArrayList<>(); // 正确
```

`ArrayList` 内部存的是 `Object[]`，而 `int` 不是 `Object` 的子类，根本放不进去。泛型 `<T>` 也只接受引用类型。

### 卡点二：基本类型没法表示"没有值"

```java
int age = 0;        // 0 到底是"年龄是0岁"还是"还没填年龄"？分不清
Integer age = null; // null 明确表示"没有值"
```

数据库字段可以为 `NULL`，表单可以不填。用 `int` 的话，你只能拿 `0` 或 `-1` 这种"魔法值"硬凑，含义全靠注释约定。用 `Integer` 就能用 `null` 直接表达。这是实体类字段**普遍用包装类而不用基本类型**的主要原因。

### 卡点三：基本类型上调不了方法

```java
int n = 10;
n.toBinaryString();         // 错误：int 没有方法

Integer n = 10;
System.out.println(Integer.toBinaryString(n));  // 1010
System.out.println(Integer.MAX_VALUE);          // 2147483647
```

字符串转数字、进制转换、取类型最大值这些操作，都需要一个**类**来承载。

**包装类就是把基本类型"包"进一个对象里**，让它能进集合、能表示 null、能带方法。

---

## 2. 八种包装类对照表

| 基本类型 | 包装类 | 占用 | 默认值 | 包装类默认值 |
|---------|--------|------|-------|------------|
| `byte` | `Byte` | 1 字节 | `0` | `null` |
| `short` | `Short` | 2 字节 | `0` | `null` |
| `int` | **`Integer`** | 4 字节 | `0` | `null` |
| `long` | `Long` | 8 字节 | `0L` | `null` |
| `float` | `Float` | 4 字节 | `0.0f` | `null` |
| `double` | `Double` | 8 字节 | `0.0d` | `null` |
| `char` | **`Character`** | 2 字节 | `'\u0000'` | `null` |
| `boolean` | `Boolean` | 1 位（JVM 相关） | `false` | `null` |

**两个必须记住的不规则命名**：

- `int` → `Integer`（不是 `Int`）
- `char` → `Character`（不是 `Char`）

其余六个都是首字母大写，很好记。

> 包装类全部位于 `java.lang` 包下，**不需要 import** 就能直接用。

---

## 3. 三个共同特征

### 特征一：都是 final 类，不能被继承

```java
class MyInteger extends Integer { }   // 编译错误：cannot inherit from final Integer
```

### 特征二：都是不可变的（immutable）

对象一旦创建，里面包的值就**永远不会变**。所有"修改"操作实际都是创建新对象：

```java
Integer a = 10;
a = a + 5;      // 不是把 a 里的 10 改成 15，而是新建了一个值为 15 的 Integer，让 a 指向它
```

这和 `String` 的不可变性是同一个道理。好处是线程安全、可以放心共享。

### 特征三：继承关系分两类

实测各包装类的父类（通过 `getClass().getSuperclass()` 获得）：

```
Integer 的父类   : java.lang.Number
Double 的父类    : java.lang.Number
Character 的父类 : java.lang.Object
Boolean 的父类   : java.lang.Object
```

**6 个数值包装类**（`Byte`/`Short`/`Integer`/`Long`/`Float`/`Double`）继承自抽象类 `Number`；`Character` 和 `Boolean` 直接继承 `Object`（因为字符和布尔值不是数字）。

```
        Object
       /   |   \
  Number  Character  Boolean
  /  |  \
Byte Integer Double ...（共6个）
```

继承 `Number` 带来的能力是**任意数值类型之间互转**：

```java
Double d = 3.99;
System.out.println(d.intValue());     // 3   ← 直接截断小数，不是四舍五入

Integer i = 300;
System.out.println(i.byteValue());    // 44  ← 溢出了！byte 范围只有 -128~127
```

`Number` 定义了 `intValue()`、`longValue()`、`doubleValue()`、`floatValue()`、`byteValue()`、`shortValue()` 六个抽象方法。注意上面两行输出：**转换会静默丢数据**，`3.99` 变成 `3`，`300` 变成 `44`（300 的低 8 位），不会报错也不会警告。

此外，8 个包装类都实现了 `Comparable` 接口，所以能直接排序、能当 `TreeMap` 的 key。

---

## 4. 装箱与拆箱

| 术语 | 方向 | 含义 |
|------|------|------|
| **装箱**（boxing） | 基本类型 → 包装类 | 把 `int` 包进 `Integer` 对象 |
| **拆箱**（unboxing） | 包装类 → 基本类型 | 从 `Integer` 对象里取出 `int` |

### 手动装箱拆箱（Java 5 之前只能这样写）

```java
// 手动装箱
Integer obj = Integer.valueOf(100);

// 手动拆箱
int num = obj.intValue();
```

### 自动装箱拆箱（Java 5 起，现在的标准写法）

```java
Integer obj = 100;      // 自动装箱，编译器替你写成 Integer.valueOf(100)
int num = obj;          // 自动拆箱，编译器替你写成 obj.intValue()
```

**关键认知：自动装箱是"语法糖"，不是新功能。** 编译器在编译期把它翻译成了方法调用，字节码里看到的还是 `Integer.valueOf()` 和 `intValue()`。理解这一点，后面几个坑就全都讲得通了。

### 哪些场景会悄悄触发自动装箱拆箱

```java
List<Integer> list = new ArrayList<>();
list.add(10);                    // 装箱：int 10 → Integer

Integer a = 10, b = 20;
int sum = a + b;                 // 拆箱：算术运算必须用基本类型
System.out.println(a > b);       // 拆箱：比较运算也要拆

Integer count = 0;
count++;                         // 先拆箱 +1，再装箱，产生一个新对象
```

记住这条：**只要包装类参与算术运算（`+ - * / %`）或大小比较（`> < >= <=`），就必然发生拆箱。** 这是后面 NPE 坑的根源。

### 不要用 new Integer()

```java
Integer x = new Integer(5);   // 别这么写
```

JDK 21 编译这行会警告：

```
警告: [removal] Integer 中的 Integer(int) 已过时, 且标记为待删除
```

这些构造方法从 **Java 9 起被废弃**，Java 16 起标记为"待删除"。原因是它**每次都强制创建新对象**，绕过了缓存池，白白浪费内存。

正确写法：

```java
Integer x = 5;                  // 自动装箱（推荐）
Integer y = Integer.valueOf(5); // 显式调用（等价）
```

---

## 5. 最大的坑：整数缓存池与 ==

这是包装类**面试必考、实战必踩**的问题。先看实测结果：

```java
Integer a = 127, b = 127;
Integer c = 128, d = 128;

System.out.println(a == b);      // true
System.out.println(c == d);      // false   ← 为什么？！
System.out.println(c.equals(d)); // true
```

同样的写法，`127` 相等，`128` 就不相等了。

### 原因：IntegerCache

`Integer.valueOf()` 内部有一个缓存数组，**预先创建好了 -128 到 127 这 256 个 Integer 对象**。调用时：

- 值在 **-128 ~ 127** 之间 → 直接从缓存里拿**同一个对象**
- 超出这个范围 → `new Integer(...)` 创建**新对象**

而 `==` 比较的是**对象地址**：

```java
Integer a = 127;   // 从缓存拿，a 和 b 指向同一个对象 → == 为 true
Integer b = 127;

Integer c = 128;   // 各自 new 了一个新对象，地址不同 → == 为 false
Integer d = 128;
```

边界实测：

```
-128 == -128 : true     ← 在缓存范围内
-129 == -129 : false    ← 超出下界
 127 ==  127 : true
 128 ==  128 : false    ← 超出上界
```

### 为什么要设计这个缓存

日常程序里的整数绝大多数是小数值（循环计数、状态码、数组下标）。提前缓存一批复用，能显著减少对象创建和 GC 压力。这个范围也不是巧合 —— 正好是 `byte` 的取值范围。

### 八个包装类的缓存情况（实测）

| 包装类 | 有缓存 | 缓存范围 |
|--------|-------|---------|
| `Byte` | ✅ | 全部（-128 ~ 127，本来就只有这些值） |
| `Short` | ✅ | -128 ~ 127 |
| `Integer` | ✅ | -128 ~ 127 |
| `Long` | ✅ | -128 ~ 127 |
| `Character` | ✅ | 0 ~ 127 |
| `Boolean` | ✅ | `TRUE` / `FALSE` 两个常量，全部缓存 |
| `Float` | ❌ | **无缓存** |
| `Double` | ❌ | **无缓存** |

实测验证：

```
Long 127      : true        Long 128      : false
Character 127 : true        Character 128 : false
Boolean true  : true
Double 1.0    : false       ← 浮点数没有缓存，永远是新对象
```

`Float` 和 `Double` 没有缓存，因为浮点数在任意区间内都有无限多个取值，缓存没有意义。

### 结论：怎么比较才对

```java
Integer a = 1000, b = 1000;

a == b           // ❌ 错，比的是地址，结果不可靠
a.equals(b)      // ✅ 对，比的是值
Objects.equals(a, b)        // ✅ 更好，a 为 null 也不会抛异常
a.intValue() == b.intValue() // ✅ 也对，手动拆箱后比值
```

**一条规则：包装类之间比较值，永远用 `equals()`，不要用 `==`。**

### 一个例外：和基本类型比较时可以用 ==

```java
Integer big1 = 1000;
int big2 = 1000;
System.out.println(big1 == big2);   // true ← 实测确认
```

这里为什么是 `true`？因为 `==` 的两边只要**有一边是基本类型**，编译器就会把另一边**拆箱**，然后按数值比较，不再比地址。

所以：

| 比较 | 行为 |
|------|------|
| 包装类 `==` 包装类 | 比**地址**（有坑） |
| 包装类 `==` 基本类型 | 拆箱后比**数值**（安全，但可能 NPE） |

> **提示**：缓存上界可以用 JVM 参数 `-XX:AutoBoxCacheMax=1000` 调大。但**绝不要依赖这个** —— 你的代码正确性不该取决于启动参数。

---

## 6. 第二大坑：自动拆箱引发的 NPE

包装类可以是 `null`，而拆箱要调用 `intValue()` 这类实例方法。**对 `null` 调方法 = `NullPointerException`。**

### 场景一：直接赋值给基本类型

```java
Integer nullInt = null;
int x = nullInt;        // NullPointerException（实测确认）
```

编译器翻译成 `int x = nullInt.intValue()`，对 `null` 调方法就炸了。这行代码**编译完全通过**，只在运行时报错。

### 场景二：和基本类型比较

```java
Integer nullInt = null;
if (nullInt == 100) { }   // NullPointerException（实测确认）
if (nullInt == null) { }  // 安全，true —— 和 null 比不触发拆箱
```

这个特别隐蔽：看起来只是个比较，却因为右边是 `int` 而触发了拆箱。

### 场景三：三元运算符（最隐蔽）

```java
Integer val = null;
boolean flag = false;

Integer r = flag ? 1 : val;   // NullPointerException（实测确认）
```

你可能会想："`flag` 是 `false`，走的是 `val` 分支，直接把 `null` 赋给 `r` 不就行了？"

问题在于 **Java 的三元运算符会先统一两个分支的类型**。一个分支是 `int`（字面量 `1`），另一个是 `Integer`，按类型提升规则，整个表达式的类型被定为 `int`。于是 `val` 必须拆箱 → NPE。

改法：把两个分支的类型统一成包装类。

```java
Integer r = flag ? Integer.valueOf(1) : val;   // 安全，r 得到 null
```

### 场景四：Map.get() 的返回值

这是实际项目中最常见的发生方式：

```java
Map<String, Integer> scores = new HashMap<>();
int score = scores.get("张三");   // key 不存在 → get 返回 null → 拆箱 NPE
```

安全写法：

```java
int score = scores.getOrDefault("张三", 0);   // 推荐
```

### 防范清单

```java
// 1. 拆箱前判空
if (num != null) {
    int x = num;
}

// 2. 用 Map 的 getOrDefault
int score = map.getOrDefault(key, 0);

// 3. 比较用 equals 或 Objects.equals
if (Objects.equals(num, 100)) { }

// 4. 明确不需要 null 的地方，就用基本类型
int count = 0;    // 而不是 Integer count = 0;
```

**最后一条最重要**：局部变量、计数器、循环变量这些确定不需要表示"空值"的场景，**直接用基本类型**。能用 `int` 就别用 `Integer`，坑和开销都省了。

---

## 7. 第三个坑：equals 不做类型转换

`equals()` 比较的前提是**两个对象类型相同**。包装类的 `equals()` 内部会先用 `instanceof` 检查类型，类型不对直接返回 `false`。

实测结果：

```java
Integer.valueOf(1).equals(1L)    // false   ← Integer 和 Long，类型不同
Long.valueOf(1).equals(1)        // false   ← 同上，反过来也一样
Long.valueOf(1).equals(1L)       // true    ← 类型一致才行
```

第一行的 `1L` 会被自动装箱成 `Long`，`Integer.equals(Long)` 发现类型不匹配，返回 `false`。**数值明明都是 1，结果却是 false。**

对比一下用 `==`：

```java
Integer.valueOf(1) == 1L         // true    ← 实测确认
```

这里 `==` 右边是基本类型 `long`，于是左边拆箱成 `int`，再提升为 `long`，按**数值**比较 → `true`。

**所以同一组数据，`equals` 说不等、`==` 说相等** —— 方向正好和第 5 节的坑相反，非常容易搞混。

### 怎么避免

跨类型比较数值时，**统一到同一个类型再比**：

```java
Integer i = 1;
Long l = 1L;

// ❌ 错
i.equals(l);

// ✅ 对：都转成 long 比较
i.longValue() == l.longValue();

// ✅ 对：用 compare
Long.compare(i.longValue(), l) == 0;
```

### 附带一个高频坑：List.remove() 的重载

`List<Integer>` 有两个 `remove` 方法，一个收 `int`（按索引删），一个收 `Object`（按对象删）。实测：

```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3));

list.remove(1);                      // 删索引 1 → [1, 3]
list.remove(Integer.valueOf(1));     // 删元素 1 → [2, 3]
```

写 `remove(1)` 时，编译器优先选**不需要装箱**的 `remove(int)` 重载，于是删掉的是**下标为 1 的元素**（也就是 `2`），而不是值为 `1` 的元素。想按值删，必须显式包装成 `Integer.valueOf(1)`。

---

## 8. 字符串与基本类型的互相转换

这是包装类在日常开发中**用得最多**的功能。

### 字符串 → 基本类型：parseXxx()

```java
int i       = Integer.parseInt("123");
long l      = Long.parseLong("456");
double d    = Double.parseDouble("3.14");
boolean b   = Boolean.parseBoolean("true");
```

`Character` 没有 `parseChar()`，取字符用 `charAt(0)`：

```java
char c = "abc".charAt(0);   // 'a'
```

### 字符串 → 包装类：valueOf()

```java
Integer i = Integer.valueOf("123");   // 返回 Integer 对象
int j     = Integer.parseInt("123");  // 返回 int 基本类型
```

两者的唯一区别是**返回类型**。有自动装箱在，日常用哪个差别不大；但明确要基本类型时用 `parseInt` 更直接（`valueOf` 内部也是先调 `parseInt`）。

### 基本类型 → 字符串：三种方式

```java
int num = 100;

String s1 = String.valueOf(num);     // 推荐，num 为 null 也安全（输出 "null"）
String s2 = Integer.toString(num);   // 也可以
String s3 = num + "";                // 能用，但会多创建 StringBuilder，不推荐
```

### parseInt 失败会抛什么异常（实测）

```
parseInt("12a") -> NumberFormatException: For input string: "12a"
parseInt(null)  -> NumberFormatException
parseInt("")    -> NumberFormatException
```

三种情况都是 `NumberFormatException`（注意 `null` 也是这个异常，**不是** `NullPointerException`）。它是 `RuntimeException` 的子类，属于非受检异常，**编译器不会强制你处理**，所以很容易漏。

处理用户输入时务必包起来：

```java
public static int safeParse(String s, int defaultValue) {
    if (s == null || s.isBlank()) return defaultValue;
    try {
        return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
        return defaultValue;
    }
}
```

### 关于 Boolean.parseBoolean 的特殊规则（实测）

```java
Boolean.parseBoolean("TRUE")   // true   ← 忽略大小写
Boolean.parseBoolean("yes")    // false  ← 不是 "true" 就是 false
Boolean.parseBoolean("1")      // false
Boolean.parseBoolean(null)     // false  ← 不抛异常
```

它只认 `"true"`（忽略大小写），其他一切输入**都返回 `false`，而且不报错**。所以用它校验用户输入是不行的 —— 输入 `"ture"` 拼错了，你只会得到一个静默的 `false`。

---

## 9. 常用方法与常量速查

### 常量（实测值）

```java
Integer.MAX_VALUE    // 2147483647
Integer.MIN_VALUE    // -2147483648
Long.MAX_VALUE       // 9223372036854775807
Double.MAX_VALUE     // 1.7976931348623157E308

Integer.SIZE         // 32（位数）
Integer.BYTES        // 4（字节数）
Boolean.TRUE / Boolean.FALSE   // 预置的 Boolean 对象
```

记住 `Integer.MAX_VALUE` 是 **21 亿左右**。超过它就会溢出：

```java
int big = Integer.MAX_VALUE;
System.out.println(big + 1);   // -2147483648   ← 溢出成了最小值，不报错！
```

这也是为什么金额、ID、时间戳这类可能很大的数值要用 `long`。

### Integer 的常用静态方法（实测输出）

| 方法 | 示例 | 结果 |
|------|------|------|
| `parseInt(String)` | `Integer.parseInt("123")` | `123` |
| `valueOf(int)` | `Integer.valueOf(5)` | `Integer` 对象 |
| `toString(int)` | `Integer.toString(10)` | `"10"` |
| `toBinaryString(int)` | `Integer.toBinaryString(10)` | `"1010"` |
| `toHexString(int)` | `Integer.toHexString(255)` | `"ff"` |
| `toOctalString(int)` | `Integer.toOctalString(8)` | `"10"` |
| `compare(int, int)` | `Integer.compare(3, 5)` | `-1` |
| `max/min/sum` | `Integer.sum(3, 5)` | `8` |

`compare(a, b)` 的返回值规则：`a < b` 返回负数，`a == b` 返回 `0`，`a > b` 返回正数。写比较器时常用。

### Character 的常用方法（实测）

| 方法 | 示例 | 结果 |
|------|------|------|
| `isDigit(char)` | `Character.isDigit('7')` | `true` |
| `isLetter(char)` | `Character.isLetter('a')` | `true` |
| `isLetterOrDigit(char)` | `Character.isLetterOrDigit('_')` | `false` |
| `isUpperCase(char)` | `Character.isUpperCase('A')` | `true` |
| `isWhitespace(char)` | `Character.isWhitespace(' ')` | `true` |
| `toUpperCase(char)` | `Character.toUpperCase('a')` | `'A'` |
| `toLowerCase(char)` | `Character.toLowerCase('A')` | `'a'` |

这组方法在做字符串校验时非常实用：

```java
// 判断字符串是否全是数字
public static boolean isAllDigits(String s) {
    if (s == null || s.isEmpty()) return false;
    for (char c : s.toCharArray()) {
        if (!Character.isDigit(c)) return false;
    }
    return true;
}
```

---

## 10. 浮点包装类的特殊情况

`Float` 和 `Double` 除了没有缓存，还有两个反直觉的行为。实测：

```
0.1 + 0.2                    = 0.30000000000000004
0.0 == -0.0                  : true
Double.valueOf(0.0).equals(-0.0) : false     ← 和 == 相反！
Double.NaN == Double.NaN     : false
Double.valueOf(NaN).equals(NaN)  : true      ← 又和 == 相反！
```

### 为什么会这样

`Double.equals()` 的实现不是直接比较数值，而是**比较两个数的二进制位（通过 `doubleToLongBits`）**：

- `0.0` 和 `-0.0` 的数值相等（`==` 为 `true`），但符号位不同，二进制位不同 → `equals` 为 `false`
- `NaN` 按 IEEE 754 规定和任何值都不相等（包括自己，`==` 为 `false`），但两个 `NaN` 的二进制位相同 → `equals` 为 `true`

### 实践结论

| 要做的事 | 正确做法 |
|---------|---------|
| 判断两个浮点数是否相等 | `Math.abs(a - b) < 1e-9`（设定误差容忍） |
| 需要精确计算（金额） | 用 **`BigDecimal`**，且用字符串构造 |
| 比较大小 / 排序 | `Double.compare(a, b)` |

```java
// ❌ 错：金额绝不能用 double
double price = 0.1 + 0.2;          // 0.30000000000000004

// ✅ 对
BigDecimal p = new BigDecimal("0.1").add(new BigDecimal("0.2"));  // 0.3
```

注意 `BigDecimal` 必须用**字符串**构造。`new BigDecimal(0.1)` 传 double 进去，误差已经产生了，再精确也没用。

---

## 11. 性能：别在循环里装箱

实测对比（1000 万次累加，JDK 21）：

```java
long sum1 = 0;                        // 基本类型
for (int i = 0; i < 10_000_000; i++) sum1 += i;
// 耗时: 10 ms

Long sum2 = 0L;                       // 包装类
for (int i = 0; i < 10_000_000; i++) sum2 += i;
// 耗时: 126 ms
```

**相差 12 倍以上。**

原因：`sum2 += i` 这一行实际做了三件事 —— 拆箱、相加、**重新装箱创建一个新 Long 对象**。循环 1000 万次，就是 1000 万个临时对象（超出缓存范围的部分），全要 GC 来清理。

> 具体数字和机器、JVM 版本有关，但**包装类慢一个数量级**这个结论是稳定的。

### 实践建议

```java
// ❌ 累加器用包装类
Long total = 0L;
for (Order o : orders) total += o.getAmount();

// ✅ 用基本类型，循环结束后再装箱（如果需要）
long total = 0L;
for (Order o : orders) total += o.getAmount();
```

一条简单的判断标准：

| 场景 | 用什么 |
|------|-------|
| 局部变量、计数器、循环变量、累加器 | **基本类型** |
| 实体类字段（可能为 null，对应数据库 NULL） | **包装类** |
| 集合元素、泛型参数 | **只能用包装类** |
| 方法返回值需要表达"无结果" | 包装类（或 `Optional`） |

不要为了"统一风格"把所有 `int` 都写成 `Integer`，也不要在实体类里用 `int` 导致分不清"0"和"未填写"。

---

## 12. 完整示例

一个表单处理场景，把前面的知识点串起来：

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 实体类：字段用包装类，因为"未填写"需要用 null 表达
class UserForm {
    private String name;
    private Integer age;        // null = 用户没填年龄
    private Double height;      // null = 用户没填身高
    private Boolean subscribed; // null = 用户没做选择

    public UserForm(String name, Integer age, Double height, Boolean subscribed) {
        this.name = name;
        this.age = age;
        this.height = height;
        this.subscribed = subscribed;
    }

    public String getName() { return name; }
    public Integer getAge() { return age; }
    public Double getHeight() { return height; }
    public Boolean getSubscribed() { return subscribed; }

    // 安全地取年龄：没填就返回默认值，避免调用方拆箱 NPE
    public int getAgeOrDefault(int defaultValue) {
        return age != null ? age : defaultValue;
    }

    @Override
    public String toString() {
        return "UserForm{name='" + name + "', age=" + age
             + ", height=" + height + ", subscribed=" + subscribed + "}";
    }
}

public class WrapperDemo {

    // 安全解析：输入非法时返回 null，而不是抛异常
    static Integer parseAgeOrNull(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            return Integer.valueOf(input.trim());
        } catch (NumberFormatException e) {
            System.out.println("  [警告] 无法解析为数字: \"" + input + "\"");
            return null;
        }
    }

    public static void main(String[] args) {

        System.out.println("--- 1. 解析表单输入（字符串 → 包装类）---");
        String[] rawAges = {"25", " 30 ", "", null, "abc"};
        List<Integer> ages = new ArrayList<>();
        for (String raw : rawAges) {
            Integer age = parseAgeOrNull(raw);
            ages.add(age);                 // 集合可以装 null
            System.out.println("输入 " + (raw == null ? "null" : "\"" + raw + "\"")
                             + " → " + age);
        }

        System.out.println("--- 2. 跳过 null 做统计（避免拆箱 NPE）---");
        int sum = 0;        // 累加器用基本类型，性能更好
        int validCount = 0;
        for (Integer age : ages) {
            if (age == null) continue;     // 关键：拆箱前判空
            sum += age;                    // 这里才拆箱，安全
            validCount++;
        }
        System.out.println("有效数据 " + validCount + " 条，合计 " + sum
                         + "，平均 " + (validCount == 0 ? 0 : sum / validCount));

        System.out.println("--- 3. 包装类的比较 ---");
        UserForm u1 = new UserForm("张三", 1000, 175.5, true);
        UserForm u2 = new UserForm("李四", 1000, 175.5, true);
        Integer a1 = u1.getAge(), a2 = u2.getAge();
        System.out.println("a1 == a2            : " + (a1 == a2)       + "  ← 超出缓存，比地址");
        System.out.println("a1.equals(a2)       : " + a1.equals(a2)    + "  ← 正确做法");
        System.out.println("Objects.equals(a1,a2): " + Objects.equals(a1, a2) + "  ← 更安全");

        System.out.println("--- 4. 处理未填写的字段 ---");
        UserForm u3 = new UserForm("王五", null, null, null);
        System.out.println(u3);
        System.out.println("年龄取默认值: " + u3.getAgeOrDefault(18));
        // int bad = u3.getAge();   // 这行会抛 NullPointerException

        System.out.println("--- 5. Map 的安全取值 ---");
        Map<String, Integer> scores = new HashMap<>();
        scores.put("张三", 90);
        System.out.println("张三 → " + scores.getOrDefault("张三", 0));
        System.out.println("赵六 → " + scores.getOrDefault("赵六", 0) + "  ← key 不存在，用默认值");
        // int bad = scores.get("赵六");   // 这行会抛 NullPointerException

        System.out.println("--- 6. 常用工具方法 ---");
        System.out.println("Integer.MAX_VALUE        = " + Integer.MAX_VALUE);
        System.out.println("MAX_VALUE + 1 (溢出)     = " + (Integer.MAX_VALUE + 1));
        System.out.println("Integer.toBinaryString(10) = " + Integer.toBinaryString(10));
        System.out.println("Integer.compare(3, 5)      = " + Integer.compare(3, 5));
        System.out.println("'7' 是数字吗              = " + Character.isDigit('7'));

        System.out.println("--- 7. 浮点数比较 ---");
        double d1 = 0.1 + 0.2, d2 = 0.3;
        System.out.println("0.1+0.2       = " + d1);
        System.out.println("直接 == 0.3    : " + (d1 == d2) + "  ← 不可靠");
        System.out.println("误差范围内相等 : " + (Math.abs(d1 - d2) < 1e-9) + "  ← 正确做法");
    }
}
```

实际运行输出：

```
--- 1. 解析表单输入（字符串 → 包装类）---
输入 "25" → 25
输入 " 30 " → 30
输入 "" → null
输入 null → null
  [警告] 无法解析为数字: "abc"
输入 "abc" → null
--- 2. 跳过 null 做统计（避免拆箱 NPE）---
有效数据 2 条，合计 55，平均 27
--- 3. 包装类的比较 ---
a1 == a2            : false  ← 超出缓存，比地址
a1.equals(a2)       : true  ← 正确做法
Objects.equals(a1,a2): true  ← 更安全
--- 4. 处理未填写的字段 ---
UserForm{name='王五', age=null, height=null, subscribed=null}
年龄取默认值: 18
--- 5. Map 的安全取值 ---
张三 → 90
赵六 → 0  ← key 不存在，用默认值
--- 6. 常用工具方法 ---
Integer.MAX_VALUE        = 2147483647
MAX_VALUE + 1 (溢出)     = -2147483648
Integer.toBinaryString(10) = 1010
Integer.compare(3, 5)      = -1
'7' 是数字吗              = true
--- 7. 浮点数比较 ---
0.1+0.2       = 0.30000000000000004
直接 == 0.3    : false  ← 不可靠
误差范围内相等 : true  ← 正确做法
```

注意第 1 组输出里 `" 30 "` 带空格也成功解析成了 `30` —— 因为代码里调了 `trim()`。`Integer.parseInt` 本身**不会**自动去空格，`parseInt(" 30 ")` 会抛 `NumberFormatException`。

---

## 13. 常见误区

| 误区 | 正确理解 |
|------|---------|
| `Integer` 比较可以用 `==` | 只在 -128~127 内"碰巧"正确，必须用 `equals()` |
| 缓存范围是 0~127 | 是 **-128~127**（`Character` 才是 0~127，因为字符没有负数） |
| 所有包装类都有缓存 | `Float` 和 `Double` **没有** |
| `int` 和 `Integer` 完全一样 | `Integer` 可以为 `null`、是对象、有方法、能进集合；`int` 都不行 |
| `Integer a = null; int b = a;` 只是赋值 | 会自动拆箱，抛 `NullPointerException` |
| `equals` 能跨类型比数值 | `Integer.valueOf(1).equals(1L)` 是 **false** |
| `Integer.parseInt(null)` 抛 NPE | 抛的是 **`NumberFormatException`** |
| `new Integer(5)` 是标准写法 | 已废弃且待删除，用 `Integer.valueOf(5)` 或直接赋值 |
| 包装类可以继承 | 全部是 `final` 类 |
| 包装类的值能修改 | 全部**不可变**，`a = a + 1` 是创建新对象 |
| `list.remove(1)` 删的是元素 1 | 删的是**索引** 1，按值删要写 `remove(Integer.valueOf(1))` |
| `double` 能算钱 | 有精度误差，金额用 `BigDecimal`（字符串构造） |
| 实体类字段用 `int` 更省事 | 分不清"值是 0"和"没填写"，该用包装类 |

### 一个综合判断题

```java
Integer a = 100, b = 100;
Integer c = 200, d = 200;
int e = 200;

System.out.println(a == b);        // true   缓存内，同一对象
System.out.println(c == d);        // false  超出缓存，不同对象
System.out.println(c == e);        // true   和基本类型比，拆箱比数值
System.out.println(c.equals(d));   // true   比值
System.out.println(c.equals(200L));// false  类型不同（Integer vs Long）
```

五行里有三种不同的比较机制在起作用。**不想记这些，就只用 `equals()`。**

---

## 14. 练习题

1. 写一个方法 `Integer safeParse(String s)`，输入合法数字字符串返回对应 `Integer`，输入 `null`、空串或非数字返回 `null`。用 `{"42", "-7", "", null, "3.14", "abc", " 8 "}` 测试。

2. 预测下面每行的输出，再运行验证：

```java
Integer a = 127, b = 127;
Integer c = 128, d = 128;
Long e = 127L;
int f = 128;

System.out.println(a == b);
System.out.println(c == d);
System.out.println(c == f);
System.out.println(a.equals(b));
System.out.println(a.equals(e));
System.out.println(e.equals(127L));
```

3. 下面三段代码各会不会抛异常？抛什么异常？为什么？

```java
// A
Integer x = null;
System.out.println(x == null);

// B
Integer y = null;
System.out.println(y == 0);

// C
Map<String, Integer> m = new HashMap<>();
int z = m.get("不存在的key");
```

4. 写一个方法统计 `List<Integer>` 的平均值，要求：列表里可能有 `null` 元素（跳过它们），列表可能为空或为 `null`（返回 0）。注意累加器用什么类型。

5. 解释：为什么 `Double.valueOf(0.0).equals(-0.0)` 是 `false`，而 `0.0 == -0.0` 是 `true`？

6. 思考题：下面这段代码为什么慢？改成什么样能快一个数量级？

```java
Integer sum = 0;
for (int i = 0; i < 1000000; i++) {
    sum += i;
}
```

<details>
<summary>第 2 题参考答案</summary>

```
a == b        → true    127 在缓存范围内，a 和 b 是同一个对象
c == d        → false   128 超出缓存，各自创建新对象，地址不同
c == f        → true    f 是 int，触发 c 拆箱，按数值比较
a.equals(b)   → true    比较值
a.equals(e)   → false   Integer 和 Long 类型不同，equals 直接返回 false
e.equals(127L)→ true    都是 Long，类型和值都相同
```

</details>

<details>
<summary>第 3 题参考答案</summary>

| 代码 | 结果 | 原因 |
|------|------|------|
| A | 正常输出 `true` | 和 `null` 比较**不会**触发拆箱，这是安全的 |
| B | **NullPointerException** | 右边 `0` 是 `int`，触发 `y.intValue()`，对 `null` 调方法 |
| C | **NullPointerException** | `get()` 返回 `null`，赋值给 `int` 时自动拆箱。应改用 `getOrDefault(key, 0)` |

B 和 C 的共同点：**NPE 的真正位置不是 `null` 本身，而是编译器插入的那次拆箱调用。**

</details>

<details>
<summary>第 6 题参考答案</summary>

慢的原因：`sum` 是 `Integer`，`sum += i` 每次循环都要**拆箱 → 相加 → 重新装箱**。100 万次循环产生约 100 万个临时 `Integer` 对象（绝大多数超出 -128~127 缓存范围），大量内存分配和 GC 开销。

改法：把累加器改成基本类型。

```java
int sum = 0;    // 或 long sum = 0，防溢出
for (int i = 0; i < 1000000; i++) {
    sum += i;
}
```

本文第 11 节的实测显示，1000 万次累加下两种写法差 12 倍（10 ms vs 126 ms）。

补充一点：这个循环的和是 499999500000，**超过了 `int` 的上限** 21 亿，所以实际应该用 `long sum = 0`，否则结果会溢出成错误的数。

</details>

---

## 一句话总结

包装类是把基本类型"装进对象"的 8 个 `final` 不可变类，存在的理由是：**集合和泛型只认对象，而 `null` 能表达"没有值"**。

初学者记住三条就能避开绝大多数坑：

1. **比较值永远用 `equals()`**，不要用 `==`（缓存池只在 -128~127 内让 `==` 碰巧正确）。
2. **拆箱前先判空**，`Integer a = null; int b = a;` 会抛 NPE；`Map.get()` 换成 `getOrDefault()`。
3. **能用基本类型就用基本类型**。计数器、累加器、局部变量用 `int`；只有在需要表示 null 或要进集合时才用 `Integer`。
