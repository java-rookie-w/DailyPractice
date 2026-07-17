# JVM Lab

> Deep-dive into the JVM internals — memory, GC, threads, class loading, and diagnostic tooling.

---

## Module Goals

- Understand JVM memory model and create deliberate OOM scenarios
- Master GC algorithms, tuning parameters, and log analysis
- Explore Java threading model and concurrency primitives
- Trace class loading and bytecode manipulation
- Become proficient with diagnostic tools (jstack, jmap, jstat, Arthas)

---

## Learning Path

| # | Experiment | Package | Goal |
|---|-----------|---------|------|
| JVM001 | Heap OOM | `oom/` | Trigger and analyze `OutOfMemoryError: Java heap space` |
| JVM002 | Metaspace OOM | `oom/` | Trigger `OutOfMemoryError: Metaspace` via dynamic class generation |
| JVM003 | Stack Overflow | `oom/` | Demonstrate SOE and analyze stack frames |
| JVM004 | GC Algorithm Comparison | `gc/` | Compare Serial, Parallel, CMS, G1, ZGC with visual logs |
| JVM005 | Thread State Transitions | `thread/` | Observe BLOCKED, WAITING, TIMED_WAITING states |
| JVM006 | Deadlock Detection | `thread/` | Create deadlock, diagnose with jstack |
| JVM007 | ClassLoader Hierarchy | `classloader/` | Explore Bootstrap/Ext/App classloaders and delegation |
| JVM008 | Custom ClassLoader | `classloader/` | Load classes from filesystem/network |
| JVM009 | Diagnostic Tools | `tools/` | jstack, jmap, jstat, jvisualvm, Arthas usage |

---

## Experiment Standards

- Each experiment is in a self-contained package
- Must have a `main()` method or `@Test` to run independently
- Javadoc at class level: what, why, expected result
- Include "how to break it" comments

---

## Resources

- [JVM Spec (Java 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/)
- [GC Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/)
