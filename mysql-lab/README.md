# MySQL Lab

> Database internals and performance experiments — indexes, transactions, locking, and replication.

---

## Module Goals

- Understand InnoDB internals: B+tree, clustered index, secondary index
- Master query optimization: EXPLAIN, covering indexes, index selection
- Explore transaction isolation levels and MVCC
- Practice locking scenarios: row locks, gap locks, deadlocks
- Experiment with replication and failover

---

## Learning Path

| # | Experiment | Goal |
|---|-----------|------|
| SQL001 | Index Basics | B+tree structure, clustered vs secondary index |
| SQL002 | EXPLAIN Deep Dive | Read execution plans, detect full table scans |
| SQL003 | Index Optimization | Covering index, prefix index, join optimization |
| SQL004 | Transaction Isolation | Dirty read, non-repeatable read, phantom read under each level |
| SQL005 | MVCC Analysis | Undo log, ReadView, transaction ID visibility |
| SQL006 | Lock Scenarios | Record lock, gap lock, next-key lock, deadlock detection |
| SQL007 | Slow Query Tuning | Capture slow queries, profiling, optimization loop |

---

## Experiment Standards

- Use H2 for rapid prototyping, MySQL for final validation
- Each experiment includes SQL scripts for setup and teardown
- Always run EXPLAIN before and after optimization
- Document schema, indexes, and expected query plans

---

## Resources

- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [InnoDB Architecture](https://dev.mysql.com/doc/refman/8.0/en/innodb-architecture.html)
