# BackendLab — Backend Technology Laboratory

> A long-term personal backend technology experiment repository.  
> Every concept can be **run**, **verified**, **broken**, **diagnosed**, and **summarized**.

---

## Philosophy

This is not a "learning project." It is a **laboratory**.

Each knowledge point follows the five-step loop:

1. **Run** — write minimal runnable code
2. **Verify** — observe behavior, prove it works
3. **Break** — intentionally cause failures, edge cases, mistakes
4. **Diagnose** — use tools to trace root causes
5. **Summarize** — document principles, patterns, and lessons learned

---

## Modules

| Module | Description | Status |
|--------|-------------|--------|
| [jvm-lab](jvm-lab/) | OOM scenarios, GC tuning, threading, class loading, diagnostic tools | 🏗 Active |
| [rabbitmq-lab](rabbitmq-lab/) | Simple queue, work queue, fanout, direct, topic exchanges | 🏗 Active |
| [kafka-lab](kafka-lab/) | Producers, consumers, partitioning, Streams API | 🏗 Active |
| [redis-lab](redis-lab/) | Data structures, distributed locks (Redisson & Lua), caching, cluster | ✅ Stable |
| [mysql-lab](mysql-lab/) | Indexing, query optimization, transactions, locking, replication | 🏗 Active |
| [netty-lab](netty-lab/) | NIO, event loop, pipeline, codec, HTTP/WebSocket, custom protocols | 🏗 Active |
| [springcloud-lab](springcloud-lab/) | Service discovery, config center, gateway, circuit breaker | 🏗 Active |
| [interview-notes](interview-notes/) | Interview preparation notes, concept summaries, mind maps | 📝 Notes |

---

## Experiment Numbering Convention

All demos follow a unified numbering scheme: `<MODULE_PREFIX><3-digit-number>`

| Module | Prefix | Example |
|--------|--------|---------|
| JVM | `JVM` | JVM001, JVM002 |
| RabbitMQ | `RMQ` | RMQ001, RMQ002 |
| Kafka | `KAF` | KAF001, KAF002 |
| Redis | `REDIS` | REDIS001, REDIS002 |
| MySQL | `SQL` | SQL001, SQL002 |
| Netty | `NET` | NET001, NET002 |
| Spring Cloud | `SC` | SC001, SC002 |

Each demo directory is named `demo<NN>_<short-description>`, e.g. `demo01_simple`, `demo02_workqueue`.

---

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.4 (as the runtime foundation, not the learning target)
- **Build:** Maven (multi-module)
- **Tools:** Lombok, JUnit 5

---

## Quick Start

```bash
# Build all modules
mvn clean compile

# Run a specific lab
cd jvm-lab && mvn spring-boot:run
```

---

## Project Conventions

### Package Naming
- Root group: `org.wang`
- Module packages: `org.wang.<module>lab` (e.g., `org.wang.jvmlab`, `org.wang.redislab`)

### Module Naming
- All experimental modules: `xxx-lab`
- Notes module: `interview-notes`

### Directory Structure (per lab module)
```
<module>/
├── src/main/java/org/wang/<module>lab/   # Experiment code
├── src/main/resources/                   # Config files
├── src/test/java/                        # Tests
├── docs/                                 # Learning notes & diagrams
└── README.md                             # Module overview
```

### Code Style
- Each experiment is self-contained within its package
- Use `demoNN_description` for experiment packages
- Include a class-level Javadoc explaining the experiment's goal
- All experiments should be runnable via `main()` or test methods

---

## History

This repository was refactored from **DailyPractice** in July 2026.  
The original modules (`mianshi`, `advanced-interview`, `file-share`, `springboot-learning`) were analyzed, and their valuable code was migrated to the new lab structure.

---

*Maintained as a long-term learning laboratory — designed to still make sense 5 years from now.*
