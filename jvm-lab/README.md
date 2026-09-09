# JVM Lab

> 面向面试的 JVM 实验集：每个实验都能独立运行，都对应知识库里的一个高频考点。

2026-09-08 重建：此前版本是 AI 一次性生成的「演示壳」（OOM 演示被注释掉、自定义类加载器是空类、
JIT 基准不可信、README 承诺的 `oom/` `gc/` `classloader/` `tools/` 四个包根本不存在），已全部推翻重写。

## 设计原则

- **纯 JDK**：不引入任何三方依赖，避免框架干扰对 JVM 本身的观察
- **每个类可独立运行**：都有 `main()`，类注释里写明「考点 / 运行参数 / 预期现象 / 面试要点」
- **现象优先**：能跑出真实报错或可观测差异，不做"打印一段文字假装演示"

## 包结构

| 包 | 实验 | 对应考点 |
|---|---|---|
| `memory` | HeapOomDemo、StackOverflowDemo、MetaspaceOomDemo、DirectMemoryOomDemo、ObjectLayoutDemo | 运行时数据区、对象布局、OOM 分类 |
| `gc` | GcLogDemo、ReferenceTypesDemo、CleanerDemo | 分代与 GC、四种引用、finalize 替代 |
| `classloader` | ClassLoaderHierarchyDemo、CustomClassLoaderDemo、ClassInitOrderDemo | 双亲委派、命名空间隔离、初始化时机 |
| `tools` | DeadlockDemo、CpuHighDemo、MonitorDemo、DiagnosticCommandsDemo | 排障：死锁、CPU 100%、监控、命令速查 |
| `jit` | EscapeAnalysisDemo、JitCompilationDemo | 逃逸分析、分层编译 |
| `thread` | ThreadStateDemo、ThreadPoolDemo | 线程状态、线程池参数与拒绝策略 |

## 快速开始

```bash
# 编译（Maven 不可用时用 javac 直编，无三方依赖）
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")

# 内存结构与 OOM
java -Xmx32m -XX:+HeapDumpOnOutOfMemoryError org.wang.jvmlab.memory.HeapOomDemo
java -Xss256k org.wang.jvmlab.memory.StackOverflowDemo
java -XX:MaxMetaspaceSize=32m org.wang.jvmlab.memory.MetaspaceOomDemo
java -XX:MaxDirectMemorySize=16m org.wang.jvmlab.memory.DirectMemoryOomDemo
java org.wang.jvmlab.memory.ObjectLayoutDemo

# GC
java -Xmx64m -Xmn16m -Xlog:gc* org.wang.jvmlab.gc.GcLogDemo
java -Xmx64m org.wang.jvmlab.gc.ReferenceTypesDemo
java org.wang.jvmlab.gc.CleanerDemo

# 类加载
java org.wang.jvmlab.classloader.ClassLoaderHierarchyDemo
java org.wang.jvmlab.classloader.CustomClassLoaderDemo
java org.wang.jvmlab.classloader.ClassInitOrderDemo

# 排障
java org.wang.jvmlab.tools.DeadlockDemo        # 另开终端 jstack <pid>
java org.wang.jvmlab.tools.CpuHighDemo         # 另开终端 top -Hp <pid>
java org.wang.jvmlab.tools.MonitorDemo
java org.wang.jvmlab.tools.DiagnosticCommandsDemo

# JIT
java org.wang.jvmlab.jit.EscapeAnalysisDemo
java -XX:-DoEscapeAnalysis org.wang.jvmlab.jit.EscapeAnalysisDemo   # 对比
java -XX:+PrintCompilation org.wang.jvmlab.jit.JitCompilationDemo

# 线程与线程池
java org.wang.jvmlab.thread.ThreadStateDemo
java org.wang.jvmlab.thread.ThreadPoolDemo
```

## 每个实验的输出要求

跑完一个实验，按这个模板记录：

- 运行参数
- 现象（贴关键输出）
- 结论（一句话）
- 面试话术（怎么讲 60 秒）

## 资源

- [JVM Spec (Java 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/)
- [GC Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/)
