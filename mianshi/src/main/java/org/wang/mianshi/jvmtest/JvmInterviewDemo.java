package org.wang.mianshi.jvmtest;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * =============================================================
 * JVM Interview Review — One-stop Demo for Senior Java Engineers
 * =============================================================
 *
 * <p>This class systematically covers all core JVM topics commonly asked in
 * senior-level Java interviews. Each topic is encapsulated in a standalone
 * static inner class with a {@code run()} method, allowing focused study
 * and execution of individual sections. Run {@link #main(String[])} to
 * execute all sections sequentially.</p>
 *
 * <h3>Topics Covered:</h3>
 * <ul>
 *   <li>SECTION A: JVM Runtime Data Areas (Heap, Stack, Metaspace, PC, Native Stack)</li>
 *   <li>SECTION B: Class Loading Mechanism (Delegation, Custom ClassLoader)</li>
 *   <li>SECTION C: Garbage Collection (Algorithms, Collectors, Reference Types)</li>
 *   <li>SECTION D: JVM Parameters & Monitoring (MXBeans, Runtime Info)</li>
 *   <li>SECTION E: Object Creation & Memory Allocation (TLAB, Escape Analysis)</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 *   // Run all sections at once
 *   JvmInterviewDemo.main(null);
 *
 *   // Run a specific section
 *   JvmInterviewDemo.A_MemoryModel.run();
 * }</pre>
 *
 * @author wang
 * @since 2026
 */
public class JvmInterviewDemo {

    // ============================================================
    // SECTION A: JVM Runtime Data Areas
    // ============================================================

    /**
     * Demonstrates JVM runtime data areas and their characteristics:
     * <ul>
     *   <li>Heap — shared, young gen (Eden+S0+S1) + old gen</li>
     *   <li>Stack — thread-private, stack frames (local vars, operand stack, etc.)</li>
     *   <li>Metaspace — class metadata (replaced PermGen in JDK 8)</li>
     *   <li>Program Counter — current bytecode instruction address</li>
     *   <li>Native Method Stack — native method invocations</li>
     * </ul>
     */
    public static class A_MemoryModel {
        // Static counter shared across threads (stored on heap, not thread-local)
        private static int sharedCount = 0;
        // ThreadLocal lives on heap but each thread gets its own copy (via ThreadLocalMap on Thread)
        private static final ThreadLocal<Integer> threadLocal = ThreadLocal.withInitial(() -> 0);

        public static void run() {
            System.out.println("\n========== SECTION A: JVM Runtime Data Areas ==========");

            // 1. Stack frame demo: each method call creates a new frame
            System.out.println(">>> A.1  Stack Frame Demo");
            recursiveMethod(3);

            // 2. Thread stack isolation: each thread has its own stack
            System.out.println("\n>>> A.2  Thread Stack Isolation");
            for (int i = 0; i < 3; i++) {
                final int tid = i;
                new Thread(() -> {
                    int localVar = tid * 10; // lives on this thread's stack
                    threadLocal.set(tid);
                    System.out.printf("  Thread-%d: localVar=%d, threadLocal=%d%n",
                            tid, localVar, threadLocal.get());
                }, "T-" + tid).start();
            }
            sleep(500);

            // 3. Heap — print basic heap info
            System.out.println("\n>>> A.3  Heap Memory Info");
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            System.out.printf("  Heap Init : %d MB%n",
                    memoryMXBean.getHeapMemoryUsage().getInit() / 1024 / 1024);
            System.out.printf("  Heap Used : %d MB%n",
                    memoryMXBean.getHeapMemoryUsage().getUsed() / 1024 / 1024);
            System.out.printf("  Heap Max  : %d MB%n",
                    memoryMXBean.getHeapMemoryUsage().getMax() / 1024 / 1024);

            // 4. Metaspace info
            System.out.println("\n>>> A.4  Metaspace (Non-Heap) Info");
            memoryMXBean.getNonHeapMemoryUsage();
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getName().contains("Metaspace") || pool.getName().contains("Compressed Class")) {
                    System.out.printf("  %s — Used: %d KB, Max: %d KB%n",
                            pool.getName(),
                            pool.getUsage().getUsed() / 1024,
                            pool.getUsage().getMax() / 1024);
                }
            }

            // 5. Demonstrate stack overflow (commented for safety, uncomment to observe)
            System.out.println("\n>>> A.5  StackOverflowError (caused by deep recursion)");
            System.out.println("  (Uncomment the code to trigger, or set -Xss128k and run)");
            // stackOverflowSimulation(); // will crash JVM

            // 6. Demonstrate heap OOM (commented for safety)
            System.out.println("\n>>> A.6  OutOfMemoryError: Java heap space");
            System.out.println("  (Set -Xmx20m and uncomment to observe)");
            // heapOomSimulation(); // will crash JVM
        }

        static void recursiveMethod(int depth) {
            int localOnStack = depth;  // primitive lives on this frame's stack
            Object refOnStack = new Object(); // reference on stack, object on heap
            System.out.printf("  Frame depth=%d, localOnStack=%d, object.hashCode=%s%n",
                    depth, localOnStack, Integer.toHexString(refOnStack.hashCode()));
            if (depth > 0) {
                recursiveMethod(depth - 1);
            }
        }

        @SuppressWarnings("unused")
        static void stackOverflowSimulation() {
            stackOverflowSimulation(); // infinite recursion → StackOverflowError
        }

        @SuppressWarnings("unused")
        static void heapOomSimulation() {
            List<byte[]> list = new ArrayList<>();
            while (true) {
                list.add(new byte[1024 * 1024]); // 1 MB each → OOM
            }
        }

        private static void sleep(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
        }
    }

    // ============================================================
    // SECTION B: Class Loading Mechanism
    // ============================================================

    /**
     * Demonstrates JVM class loading:
     * <ul>
     *   <li>Class loading phases: Load → Verify → Prepare → Resolve → Init</li>
     *   <li>Parent Delegation Model (双亲委派)</li>
     *   <li>Custom ClassLoader</li>
     *   <li>Breaking delegation (e.g., Tomcat WebappClassLoader, JDBC SPI)</li>
     * </ul>
     */
    public static class B_ClassLoading {
        // Static variable — initialized during "prepare" (zero) then "init" (value)
        private static int classInitValue = 42;
        // Static block — runs during initialization phase
        static {
            System.out.println("  [Static Block] B_ClassLoading initialized, value=" + classInitValue);
        }

        public static void run() {
            System.out.println("\n========== SECTION B: Class Loading Mechanism ==========");

            // 1. Display class loaders hierarchy
            System.out.println(">>> B.1  Built-in ClassLoader Hierarchy");
            ClassLoader bootstrapCL = String.class.getClassLoader();
            ClassLoader extCL = ClassLoader.getPlatformClassLoader(); // JDK 9+ equiv of ext
            ClassLoader appCL = JvmInterviewDemo.class.getClassLoader();
            System.out.printf("  Bootstrap (rt.jar / java.base): %s%n", bootstrapCL); // null in Java
            System.out.printf("  Platform/Extension:              %s%n", extCL);
            System.out.printf("  Application/System:              %s%n", appCL);
            System.out.println("  Delegation: Bootstrap ← Platform ← Application");

            // 2. Custom ClassLoader demo
            System.out.println("\n>>> B.2  Custom ClassLoader Demo");
            CustomClassLoader customCL = new CustomClassLoader();
            ClassLoader parent = customCL.getParent();
            System.out.printf("  CustomCL parent: %s (should be AppClassLoader)%n", parent);

            // 3. Show how parent delegation checks class existence
            System.out.println("\n>>> B.3  Delegation Check");
            System.out.printf("  String loaded by:    %s%n", String.class.getClassLoader());
            System.out.printf("  JvmInterviewDemo by: %s%n", JvmInterviewDemo.class.getClassLoader());
            System.out.printf("  B_ClassLoading by:   %s%n", B_ClassLoading.class.getClassLoader());

            // 4. JDBC SPI breaks delegation (conceptual demo)
            System.out.println("\n>>> B.4  Breaking Delegation (SPI mechanism)");
            System.out.println("  JDBC DriverManager uses Thread Context ClassLoader (TCCL)");
            System.out.println("  to load SPI implementations, breaking delegation.");
            System.out.printf("  Current TCCL: %s%n", Thread.currentThread().getContextClassLoader());

            // 5. Load order verification
            System.out.println("\n>>> B.5  Class Init Order");
            System.out.println("  - Parent static blocks run before child static blocks");
            System.out.println("  - static blocks run when class is first initialized");
            new ChildClassOrder();
        }
    }

    /** Simple custom ClassLoader — delegates to parent */
    static class CustomClassLoader extends ClassLoader {
        public CustomClassLoader() {
            super(ClassLoader.getSystemClassLoader()); // parent = AppClassLoader
        }
        // Delegates everything to parent (standard behavior)
    }

    static class ParentClassOrder {
        static { System.out.println("    1. Parent static block"); }
        { System.out.println("    3. Parent instance block"); }
        ParentClassOrder() { System.out.println("    4. Parent constructor"); }
    }

    static class ChildClassOrder extends ParentClassOrder {
        static { System.out.println("    2. Child static block"); }
        { System.out.println("    5. Child instance block"); }
        ChildClassOrder() { System.out.println("    6. Child constructor"); }
    }

    // ============================================================
    // SECTION C: Garbage Collection
    // ============================================================

    /**
     * Demonstrates GC concepts:
     * <ul>
     *   <li>Reference types: Strong, Soft, Weak, Phantom</li>
     *   <li>GC algorithms: Mark-Sweep, Mark-Copy, Mark-Compact</li>
     *   <li>GC collectors: Serial, Parallel, CMS, G1, ZGC</li>
     *   <li>finalize() mechanism</li>
     *   <li>GC Roots enumeration</li>
     * </ul>
     */
    public static class C_GarbageCollection {
        private static boolean keepStrongRef = true;

        public static void run() {
            System.out.println("\n========== SECTION C: Garbage Collection ==========");

            // 1. List active GC collectors
            System.out.println(">>> C.1  Active GC Collectors");
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                System.out.printf("  %s — Collections: %d, Time: %d ms%n",
                        gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
            }

            // 2. Strong Reference — never GC'd while reachable
            System.out.println("\n>>> C.2  Strong Reference (default)");
            Object strongRef = new Object();
            System.out.printf("  strongRef: %s (won't be GC'd while in scope)%n", strongRef);

            // 3. Soft Reference — GC'd only when memory is tight
            System.out.println("\n>>> C.3  Soft Reference");
            Object softTarget = new Object();
            SoftReference<Object> softRef = new SoftReference<>(softTarget);
            softTarget = null; // only softRef points to it now
            System.gc();
            System.out.printf("  Soft ref after GC: %s (survives normal GC)%n",
                    softRef.get() != null ? "still alive" : "collected");

            // 4. Weak Reference — GC'd in the next GC cycle
            System.out.println("\n>>> C.4  Weak Reference");
            Object weakTarget = new Object();
            WeakReference<Object> weakRef = new WeakReference<>(weakTarget);
            weakTarget = null;
            System.gc();
            sleep(200); // give GC some time
            System.out.printf("  Weak ref after GC: %s (should be collected)%n",
                    weakRef.get() != null ? "still alive (rare)" : "collected ✓");

            // 5. Phantom Reference — always returns null, used for cleanup
            System.out.println("\n>>> C.5  Phantom Reference");
            ReferenceQueue<Object> queue = new ReferenceQueue<>();
            Object phTarget = new Object();
            PhantomReference<Object> phantomRef = new PhantomReference<>(phTarget, queue);
            phTarget = null;
            System.gc();
            sleep(200);
            Reference<?> refFromQueue = queue.poll();
            System.out.printf("  Phantom ref.get(): %s (always null)%n", phantomRef.get());
            System.out.printf("  Enqueued: %s (cleanup trigger)%n",
                    refFromQueue != null ? "yes ✓" : "not yet");

            // 6. finalize() demo
            System.out.println("\n>>> C.6  finalize() — resurrection demo");
            FailableObject failable = new FailableObject("obj1");
            failable = null;
            System.gc();
            sleep(500); // finalize runs in Finalizer thread
            System.gc(); // second GC since first resurrected it
            System.out.printf("  FailableObject.alive = %s%n", FailableObject.alive);

            // 7. GC algorithm summary
            System.out.println("\n>>> C.7  GC Algorithm Summary");
            System.out.println("  Young GC: Mark-Copy  (fast, object mortality ~98%)");
            System.out.println("  Old GC:   Mark-Compact or Mark-Sweep (avoids fragmentation)");
            System.out.println("  G1:       Region-based, Mixed GC, predictable pause");
            System.out.println("  ZGC/Shenandoah: Concurrent, sub-1ms pause (JDK 11+/15+)");
        }

        static class FailableObject {
            static boolean alive = false;
            private String name;

            FailableObject(String name) { this.name = name; }

            @Override
            @SuppressWarnings("deprecation")
            protected void finalize() throws Throwable {
                System.out.printf("  finalize() called for %s — resurrecting!%n", name);
                alive = true; // object escapes collection once
                // Note: finalize() deprecated since JDK 9, use Cleaner / PhantomReference instead
            }
        }

        private static void sleep(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
        }
    }

    // ============================================================
    // SECTION D: JVM Parameters & Monitoring
    // ============================================================

    /**
     * Demonstrates JVM monitoring and commonly-used JVM flags.
     *
     * <h3>Essential JVM Flags (Interview Hotspot):</h3>
     * <table>
     *   <tr><td>-Xms / -Xmx</td>          <td>Initial / Max heap size</td></tr>
     *   <tr><td>-Xss</td>                 <td>Thread stack size</td></tr>
     *   <tr><td>-Xmn / -XX:NewRatio</td>  <td>Young gen size / ratio</td></tr>
     *   <tr><td>-XX:SurvivorRatio</td>    <td>Eden : S0 : S1 ratio (default 8)</td></tr>
     *   <tr><td>-XX:MetaspaceSize / -XX:MaxMetaspaceSize</td><td>Metaspace config</td></tr>
     *   <tr><td>-XX:+UseG1GC / -XX:+UseZGC</td> <td>GC collector selection</td></tr>
     *   <tr><td>-XX:MaxGCPauseMillis</td> <td>Target max GC pause (G1/ZGC)</td></tr>
     *   <tr><td>-XX:+PrintGCDetails</td>  <td>Print GC logs (JDK 8); use -Xlog:gc* for JDK 9+</td></tr>
     *   <tr><td>-XX:+HeapDumpOnOutOfMemoryError</td><td>Heap dump on OOM</td></tr>
     *   <tr><td>-XX:MaxDirectMemorySize</td><td>Direct buffer memory limit</td></tr>
     *   <tr><td>-XX:MaxTenuringThreshold</td><td>Max age before promotion to Old Gen</td></tr>
     *   <tr><td>-XX:+DoEscapeAnalysis</td><td>Escape analysis (enabled by default)</td></tr>
     * </table>
     */
    public static class D_ParametersAndMonitoring {

        public static void run() {
            System.out.println("\n========== SECTION D: JVM Parameters & Monitoring ==========");

            // 1. Runtime info
            System.out.println(">>> D.1  JVM Runtime Info");
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            System.out.printf("  VM Name   : %s%n", runtime.getVmName());
            System.out.printf("  VM Vendor : %s%n", runtime.getVmVendor());
            System.out.printf("  VM Version: %s%n", runtime.getVmVersion());
            System.out.printf("  Uptime    : %d ms%n", runtime.getUptime());
            System.out.printf("  Arguments : %s%n", runtime.getInputArguments());

            // 2. Memory pools
            System.out.println("\n>>> D.2  Memory Pool Details");
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                System.out.printf("  %-30s Type=%-10s Used=%-8d KB Max=%-8d KB%n",
                        pool.getName(),
                        pool.getType(),
                        pool.getUsage().getUsed() / 1024,
                        pool.getUsage().getMax() / 1024);
            }

            // 3. Thread info
            System.out.println("\n>>> D.3  Thread Info");
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            System.out.printf("  Total started: %d%n", threadMXBean.getTotalStartedThreadCount());
            System.out.printf("  Peak threads : %d%n", threadMXBean.getPeakThreadCount());
            System.out.printf("  Active threads: %d%n", threadMXBean.getThreadCount());
            System.out.printf("  Daemon threads: %d%n", threadMXBean.getDaemonThreadCount());

            // 4. Key parameters reference
            System.out.println("\n>>> D.4  Essential JVM Flags (Interview Quick Ref)");
            String[][] flags = {
                    {"-Xms / -Xmx", "Initial / Max heap size", "eg: -Xms2g -Xmx2g"},
                    {"-Xss", "Thread stack size", "eg: -Xss256k (default ~1M)"},
                    {"-Xmn", "Young gen size", "eg: -Xmn512m"},
                    {"-XX:NewRatio", "Old/Young ratio (default 2)", "Old : Young = 2 : 1"},
                    {"-XX:SurvivorRatio", "Eden/Survivor ratio (default 8)", "Eden : S0 : S1 = 8 : 1 : 1"},
                    {"-XX:MetaspaceSize", "Metaspace initial size", "eg: -XX:MetaspaceSize=128m"},
                    {"-XX:MaxMetaspaceSize", "Metaspace max", "eg: -XX:MaxMetaspaceSize=256m"},
                    {"-XX:+UseG1GC", "G1 collector (default since JDK 9)", ""},
                    {"-XX:+UseZGC", "ZGC (JDK 11+, low latency)", ""},
                    {"-XX:MaxGCPauseMillis", "Target GC pause", "eg: -XX:MaxGCPauseMillis=200"},
                    {"-XX:+PrintGCDetails", "GC log (JDK 8)", "JDK 9+: -Xlog:gc*"},
                    {"-XX:+HeapDumpOnOutOfMemoryError", "Dump heap on OOM", "Highly recommended!"},
                    {"-XX:MaxDirectMemorySize", "Direct memory limit", "eg: -XX:MaxDirectMemorySize=256m"},
                    {"-XX:MaxTenuringThreshold", "Max age to promote (default 15)", "CMS default 6, G1 default 15"},
            };
            for (String[] row : flags) {
                System.out.printf("  %-35s %-35s %s%n", row[0], row[1], row[2]);
            }

            // 5. CPU core count (important for GC thread count)
            System.out.println("\n>>> D.5  System Info");
            System.out.printf("  Available processors: %d%n", Runtime.getRuntime().availableProcessors());
            System.out.println("  Parallel GC threads  ≈ CPU cores");
            System.out.println("  Conc GC threads       ≈ CPU cores / 4");
        }
    }

    // ============================================================
    // SECTION E: Object Creation & Memory Allocation
    // ============================================================

    /**
     * Demonstrates object creation process and memory allocation strategies:
     * <ul>
     *   <li>Object creation steps: new → class check → allocate → init zero → set header → init</li>
     *   <li>Allocation: pointer-bump (bump-the-pointer) vs free-list</li>
     *   <li>TLAB (Thread Local Allocation Buffer) — lock-free allocation</li>
     *   <li>Escape Analysis — stack allocation, scalar replacement, lock elimination</li>
     *   <li>Object memory layout: Mark Word (8B) + Klass Pointer (4B/8B) + fields + padding</li>
     *   <li>Synchronized lock upgrade: biased → lightweight → heavyweight</li>
     * </ul>
     */
    public static class E_ObjectCreation {
        // A simple object to observe memory layout (use JOL: org.openjdk.jol)
        static class SampleObj {
            int a;          // 4 bytes (with compressed oops)
            long b;         // 8 bytes
            boolean flag;   // 1 byte
            // total fields = 13 bytes → padded to 16 bytes
            // header = 12 bytes (compressed oops) → total = 24/32 bytes depending on alignment
        }

        public static void run() {
            System.out.println("\n========== SECTION E: Object Creation & Memory Allocation ==========");

            // 1. Object creation steps
            System.out.println(">>> E.1  Object Creation Steps");
            System.out.println("  1. new instruction → check if class loaded/resolved");
            System.out.println("  2. Allocate memory on heap:");
            System.out.println("     - Pointer-bump (Serial, ParNew with compacted heap)");
            System.out.println("     - Free-list (CMS with fragmented old gen)");
            System.out.println("  3. Initialize to zero (semi-automatic, same for all allocators)");
            System.out.println("  4. Set Object Header (Mark Word + Klass Pointer)");
            System.out.println("  5. Execute <init> (constructor)");

            // 2. TLAB demo
            System.out.println("\n>>> E.2  TLAB (Thread Local Allocation Buffer)");
            System.out.println("  TLAB avoids CAS contention when allocating in Eden.");
            System.out.println("  Each thread pre-allocates a small buffer in Eden.");
            System.out.println("  -XX:+UseTLAB (enabled by default)");
            System.out.println("  -XX:TLABSize=<size>  — manually set TLAB size");

            // 3. Escape Analysis demo
            System.out.println("\n>>> E.3  Escape Analysis");
            System.out.println("  Objects that don't escape the method/thread can be:");
            System.out.println("  - Allocated on stack (stack allocation)");
            System.out.println("  - Scalar replaced (fields decomposed into locals)");
            System.out.println("  - Synchronization eliminated (lock elision)");
            System.out.println("  -XX:+DoEscapeAnalysis (enabled by default since JDK 6u23+)");
            escapeAnalysisDemo();

            // 4. Object memory layout
            System.out.println("\n>>> E.4  Object Memory Layout (64-bit with compressed oops)");
            System.out.println("  ┌──────────────┐");
            System.out.println("  │  Mark Word   │ 8 bytes (hash, GC age, lock info)");
            System.out.println("  ├──────────────┤");
            System.out.println("  │ Klass Pointer│ 4 bytes (compressed, points to class metadata)");
            System.out.println("  ├──────────────┤");
            System.out.println("  │   fields     │ instance fields (reordered for alignment)");
            System.out.println("  ├──────────────┤");
            System.out.println("  │   padding    │ aligned to 8-byte boundary");
            System.out.println("  └──────────────┘");
            System.out.println("  Tip: Use JOL (org.openjdk.jol) to print actual layout");

            // 5. Lock upgrade path
            System.out.println("\n>>> E.5  Synchronized Lock Upgrade Path");
            System.out.println("  No-lock → Biased-lock (thread ID in Mark Word)");
            System.out.println("         → Lightweight-lock (CAS spin, stack lock record)");
            System.out.println("         → Heavyweight-lock (monitor enter, OS mutex)");
            System.out.println("  -XX:-UseBiasedLocking  (deprecated in JDK 15, removed in JDK 21)");

            // 6. Allocation speed comparison
            System.out.println("\n>>> E.6  Allocation Speed (pointer-bump is fastest)");
            long start = System.nanoTime();
            for (int i = 0; i < 10_000_000; i++) {
                new Object(); // allocation benchmark (with TLAB + pointer-bump)
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("  10M Object allocations: %d ms (%.1f ns/obj)%n",
                    elapsed / 1_000_000, (double) elapsed / 10_000_000);
        }

        /** Escape analysis demo — object does NOT escape, can be stack-allocated */
        static void escapeAnalysisDemo() {
            int sum = 0;
            for (int i = 0; i < 10_000; i++) {
                // This Point object never escapes — JIT can allocate on stack
                Point p = new Point(i, i + 1);
                sum += p.x; // scalar replacement: sum += i (p is eliminated entirely)
            }
            System.out.printf("  escapeAnalysisDemo result: sum=%d (Point objects never escaped)%n", sum);
        }

        static class Point {
            final int x, y;
            Point(int x, int y) { this.x = x; this.y = y; }
        }
    }

    // ============================================================
    // SECTION F: JIT Compilation & Performance
    // ============================================================

    /**
     * Demonstrates JIT compilation concepts:
     * <ul>
     *   <li>C1 (Client) vs C2 (Server) compilers</li>
     *   <li>Tiered compilation (default since JDK 8)</li>
     *   <li>HotSpot compilation thresholds</li>
     *   <li>On-Stack Replacement (OSR)</li>
     *   <li>Code Cache management</li>
     * </ul>
     */
    public static class F_JITCompilation {

        public static void run() {
            System.out.println("\n========== SECTION F: JIT Compilation ==========");

            System.out.println(">>> F.1  JIT Compilation Overview");
            System.out.println("  Interpreter: Interprets bytecode line-by-line (slow startup)");
            System.out.println("  C1 (Client):   Fast compilation, moderate optimization");
            System.out.println("  C2 (Server):   Slow compilation, aggressive optimization");
            System.out.println("  Tiered Compilation (default JDK 8+): C1 → C2 gradual upgrade");

            System.out.println("\n>>> F.2  Compilation Thresholds");
            System.out.println("  -XX:CompileThreshold=10000    (default for C1/C2)");
            System.out.println("  -XX:Tier3InvocationThreshold  (C1 full profile)");
            System.out.println("  -XX:Tier4InvocationThreshold  (C2)");
            System.out.println("  Use -XX:+PrintCompilation to see JIT in action");

            System.out.println("\n>>> F.3  JIT Optimizations");
            System.out.println("  - Inlining           (method body inserted at call site)");
            System.out.println("  - Escape Analysis    (stack alloc, scalar replacement)");
            System.out.println("  - Lock Elision       (remove sync on thread-local objects)");
            System.out.println("  - Dead Code Elim     (remove unreachable branches)");
            System.out.println("  - Loop Unrolling     (reduce loop overhead)");

            System.out.println("\n>>> F.4  Code Cache");
            System.out.println("  -XX:ReservedCodeCacheSize=240m (default 240 MB)");
            System.out.println("  -XX:+UseCodeCacheFlushing      (flush cold methods)");
            System.out.println("  If full: JVM prints 'CodeCache is full. Compiler disabled'");

            // Demo: warm-up effect (JIT vs interpreted)
            System.out.println("\n>>> F.5  Warm-up Demo");
            warmUpDemo();
        }

        static void warmUpDemo() {
            long sum;
            // First run — interpreted + C1
            long t1 = System.nanoTime();
            sum = compute(100_000);
            t1 = System.nanoTime() - t1;

            // After warm-up — C2 optimized
            long t2 = System.nanoTime();
            sum = compute(100_000);
            t2 = System.nanoTime() - t2;

            System.out.printf("  Run 1 (interpreted): %d ms%n", t1 / 1_000_000);
            System.out.printf("  Run 2 (JIT compiled): %d ms%n", t2 / 1_000_000);
            System.out.printf("  Speedup: ~%.1fx%n", (double) t1 / t2);
            System.out.println("  (result ignored: " + sum + ")"); // prevent dead-code elimination
        }

        static long compute(int n) {
            long result = 0;
            for (int i = 0; i < n; i++) {
                result += fibonacciMod(i % 50);
            }
            return result;
        }

        static long fibonacciMod(int n) {
            if (n <= 1) return n;
            long a = 0, b = 1;
            for (int i = 2; i <= n; i++) {
                long c = a + b;
                a = b;
                b = c;
            }
            return b;
        }
    }

    // ============================================================
    // MAIN — Run All Sections
    // ============================================================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   JVM Interview Comprehensive Review            ║");
        System.out.println("║   Java: " + System.getProperty("java.version") +
                "  |  VM: " + System.getProperty("java.vm.name") + "   ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        A_MemoryModel.run();
        B_ClassLoading.run();
        C_GarbageCollection.run();
        D_ParametersAndMonitoring.run();
        E_ObjectCreation.run();
        F_JITCompilation.run();

        // ============================================================
        // INTERVIEW QUICK REFERENCE (Chinese)
        // ============================================================
        System.out.println("\n\n╔══════════════════════════════════════════════════╗");
        System.out.println("║         JVM 面试速记（中文版）                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        System.out.println("\n【1. JVM 内存模型】");
        System.out.println("  堆：所有线程共享，存放对象实例和数组。分新生代(Eden+S0+S1)和老年代");
        System.out.println("  栈：线程私有，每个方法对应一个栈帧(局部变量表、操作数栈、动态链接、返回地址)");
        System.out.println("  方法区/元空间：存储类信息、常量、静态变量、JIT编译后的代码缓存");
        System.out.println("  程序计数器：线程私有，记录当前线程执行字节码的行号指示器");
        System.out.println("  JDK 8 用 Metaspace 替代 PermGen，使用本地内存，解决了 PermGen OOM 问题");

        System.out.println("\n【2. 判断对象是否可回收】");
        System.out.println("  ① 引用计数法（Python 用，Java 不用 — 无法解决循环引用）");
        System.out.println("  ② 可达性分析（GC Roots）：从 GC Roots 向下搜索，不可达则回收");
        System.out.println("  GC Roots 包括：虚拟机栈引用、静态属性引用、常量引用、JNI 引用、活跃线程等");

        System.out.println("\n【3. 四种引用类型】");
        System.out.println("  强引用(Strong)：  new Object()，永不回收");
        System.out.println("  软引用(Soft)：    内存不足时回收，适合缓存");
        System.out.println("  弱引用(Weak)：    下一次 GC 必定回收，适合 WeakHashMap");
        System.out.println("  虚引用(Phantom)： 无法获取对象，仅用于回收跟踪，配合 ReferenceQueue 做清理");

        System.out.println("\n【4. GC 算法】");
        System.out.println("  标记-清除(Mark-Sweep)： 简单但产生碎片");
        System.out.println("  标记-复制(Mark-Copy)：  新生代常用，浪费一半空间，对象存活率低时高效");
        System.out.println("  标记-整理(Mark-Compact)：老年代常用，无碎片但耗时");

        System.out.println("\n【5. 垃圾收集器】");
        System.out.println("  ┌─────────────┬──────────────┬──────────────┐");
        System.out.println("  │  新生代      │  老年代       │  特点        │");
        System.out.println("  ├─────────────┼──────────────┼──────────────┤");
        System.out.println("  │  Serial     │  Serial Old   │ 单线程, Client │");
        System.out.println("  │  ParNew     │  CMS          │ 多线程+低延迟  │");
        System.out.println("  │  Parallel   │  Parallel Old │ 吞吐量优先     │");
        System.out.println("  │  G1(全区域)  │  G1(全区域)   │ 预测停顿       │");
        System.out.println("  │  ZGC(全区域) │  ZGC(全区域)  │ <1ms 停顿     │");
        System.out.println("  └─────────────┴──────────────┴──────────────┘");
        System.out.println("  CMS: 初始标记→并发标记→重新标记→并发清除（两次STW）");
        System.out.println("  G1:  初始标记→并发标记→最终标记→筛选回收（Mixed GC）");
        System.out.println("  JDK 9+ G1 是默认收集器，CMS JDK 14 移除");

        System.out.println("\n【6. 类加载机制】");
        System.out.println("  流程：加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载");
        System.out.println("  双亲委派：Bootstrap → Extension/Platform → Application");
        System.out.println("  好处：避免重复加载 + 保护核心类不被篡改");
        System.out.println("  打破双亲委派：Tomcat WebappClassLoader、JDBC DriverManager(SPI→TCCL)、OSGi");

        System.out.println("\n【7. 对象创建过程】");
        System.out.println("  new → 类加载检查 → 分配内存 → 初始化零值 → 设置对象头 → <init>");
        System.out.println("  内存分配：指针碰撞(Bump-the-Pointer) vs 空闲列表(Free-List)");
        System.out.println("  并发安全：CAS+失败重试 或 TLAB（线程本地分配缓冲）");
        System.out.println("  对象头：Mark Word(8B) + Klass Pointer(4B/8B，压缩后4B)");

        System.out.println("\n【8. JIT 编译】");
        System.out.println("  热点代码：多次调用的方法 + 多次执行的循环体(OSR)");
        System.out.println("  C1 快速编译，C2 深度优化，分层编译(C1→C2)默认开启");
        System.out.println("  优化：方法内联、逃逸分析、锁消除、标量替换、循环展开");

        System.out.println("\n【9. 常见 OOM 及排查】");
        System.out.println("  java.lang.OutOfMemoryError: Java heap space      → -Xmx 调大 / 排查内存泄漏");
        System.out.println("  java.lang.OutOfMemoryError: Metaspace            → -XX:MaxMetaspaceSize 调大");
        System.out.println("  java.lang.OutOfMemoryError: Direct buffer memory → -XX:MaxDirectMemorySize");
        System.out.println("  java.lang.StackOverflowError                     → -Xss 调大 / 检查递归");
        System.out.println("  排查工具：jmap -dump、jstack、jstat -gc、MAT、JProfiler");

        System.out.println("\n【10. 调优思路】");
        System.out.println("  ① 明确目标：吞吐量 / 低延迟 / 内存占用");
        System.out.println("  ② 选 GC 收集器：吞吐量→ParallelGC，延迟→G1/ZGC");
        System.out.println("  ③ 设置堆大小：-Xms=-Xmx 防止动态扩容开销, -Xmn 控制新生代");
        System.out.println("  ④ 观察日志：GC 频率、停顿时间、晋升情况");
        System.out.println("  ⑤ 逐步调优：优先调大内存，再调 GC 参数");
    }
}
