package org.wang.advanced.distributed.transaction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式事务面试题 - 九年经验高级工程师必备
 * 
 * 面试高频问题：
 * 1. 分布式事务有哪些解决方案？
 * 2. 2PC、3PC、TCC、Saga的区别？
 * 3. 最终一致性如何保证？
 * 4. 如何选择合适的分布式事务方案？
 */
public class DistributedTransaction {

    // ============================================================
    // 1. 2PC (Two-Phase Commit) 两阶段提交
    // ============================================================
    
    /**
     * 两阶段提交协议
     * 
     * 流程：
     * 1. 准备阶段(Prepare)：协调者询问参与者是否可以提交
     * 2. 提交阶段(Commit)：协调者根据参与者响应决定提交或回滚
     * 
     * 优点：数据强一致性
     * 缺点：
     * - 同步阻塞：参与者等待协调者响应
     * - 单点故障：协调者宕机导致阻塞
     * - 数据不一致：提交阶段部分参与者失败
     * 
     * 适用场景：数据库层面的分布式事务（如XA事务）
     */
    static class TwoPhaseCommit {
        private boolean[] participantReady;
        private boolean coordinatorDecision;
        
        public TwoPhaseCommit(int participantCount) {
            this.participantReady = new boolean[participantCount];
        }
        
        /**
         * 阶段1：准备阶段
         * @return true: 所有参与者准备就绪
         */
        public boolean prepare() {
            System.out.println("[2PC] 阶段1：准备阶段");
            
            // 模拟询问所有参与者
            for (int i = 0; i < participantReady.length; i++) {
                participantReady[i] = simulateParticipantPrepare(i);
                System.out.println("  参与者 " + i + " 准备状态: " + (participantReady[i] ? "就绪" : "失败"));
                
                if (!participantReady[i]) {
                    System.out.println("[2PC] 准备阶段失败，有参与者未就绪");
                    return false;
                }
            }
            
            System.out.println("[2PC] 准备阶段成功，所有参与者就绪");
            return true;
        }
        
        /**
         * 阶段2：提交阶段
         */
        public void commit() {
            System.out.println("[2PC] 阶段2：提交阶段");
            coordinatorDecision = true;
            
            for (int i = 0; i < participantReady.length; i++) {
                simulateParticipantCommit(i, true);
                System.out.println("  参与者 " + i + " 提交完成");
            }
            
            System.out.println("[2PC] 提交完成");
        }
        
        /**
         * 回滚
         */
        public void rollback() {
            System.out.println("[2PC] 回滚操作");
            coordinatorDecision = false;
            
            for (int i = 0; i < participantReady.length; i++) {
                simulateParticipantCommit(i, false);
                System.out.println("  参与者 " + i + " 回滚完成");
            }
        }
        
        private boolean simulateParticipantPrepare(int index) {
            // 模拟参与者准备（可能失败）
            return Math.random() > 0.1; // 90%成功率
        }
        
        private void simulateParticipantCommit(int index, boolean commit) {
            // 模拟参与者提交或回滚
            try {
                Thread.sleep(10); // 模拟网络延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ============================================================
    // 2. 3PC (Three-Phase Commit) 三阶段提交
    // ============================================================
    
    /**
     * 三阶段提交协议（2PC的改进版）
     * 
     * 流程：
     * 1. CanCommit阶段：询问参与者是否可以提交（轻量级）
     * 2. PreCommit阶段：准备提交（类似2PC的准备阶段）
     * 3. DoCommit阶段：真正提交
     * 
     * 改进点：
     * - 增加CanCommit阶段，减少资源锁定时间
     * - 引入超时机制，避免无限等待
     * - 参与者超时后自动提交（减少阻塞）
     * 
     * 优点：减少阻塞，提高可用性
     * 缺点：仍然可能出现数据不一致
     */
    static class ThreePhaseCommit {
        
        public void execute() {
            System.out.println("\n[3PC] 三阶段提交演示");
            
            // 阶段1：CanCommit
            System.out.println("  阶段1：CanCommit - 询问是否可以提交");
            boolean canCommit = simulateCanCommit();
            System.out.println("  CanCommit结果: " + (canCommit ? "可以提交" : "无法提交"));
            
            if (!canCommit) {
                System.out.println("[3PC] 流程终止");
                return;
            }
            
            // 阶段2：PreCommit
            System.out.println("  阶段2：PreCommit - 准备提交");
            boolean preCommit = simulatePreCommit();
            System.out.println("  PreCommit结果: " + (preCommit ? "准备成功" : "准备失败"));
            
            if (!preCommit) {
                System.out.println("[3PC] 回滚");
                return;
            }
            
            // 阶段3：DoCommit
            System.out.println("  阶段3：DoCommit - 提交");
            simulateDoCommit();
            System.out.println("[3PC] 提交完成");
        }
        
        private boolean simulateCanCommit() { return Math.random() > 0.05; }
        private boolean simulatePreCommit() { return Math.random() > 0.1; }
        private void simulateDoCommit() {
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // ============================================================
    // 3. TCC (Try-Confirm-Cancel)
    // ============================================================
    
    /**
     * TCC分布式事务
     * 
     * 流程：
     * 1. Try阶段：预留资源（冻结库存、冻结金额）
     * 2. Confirm阶段：确认提交（扣减冻结的资源）
     * 3. Cancel阶段：取消释放（解冻资源）
     * 
     * 优点：
     * - 应用层实现，不依赖数据库XA
     * - 资源锁定粒度可控
     * - 性能比2PC好
     * 
     * 缺点：
     * - 业务侵入性强（需要实现三个接口）
     * - 开发成本高
     * - 空回滚、悬挂问题需要处理
     * 
     * 适用场景：资金、库存等核心业务
     */
    interface TccService {
        boolean tryPhase(String transactionId);
        boolean confirmPhase(String transactionId);
        boolean cancelPhase(String transactionId);
    }
    
    /**
     * 库存TCC服务示例
     */
    static class InventoryTccService implements TccService {
        private final long totalStock = 100;
        private long frozenStock = 0;
        
        @Override
        public boolean tryPhase(String transactionId) {
            System.out.println("[库存TCC] Try阶段 - 预留库存");
            // 检查库存是否充足
            if (totalStock - frozenStock >= 1) {
                frozenStock++;
                System.out.println("  预留库存成功，冻结库存: " + frozenStock);
                return true;
            }
            System.out.println("  库存不足，预留失败");
            return false;
        }
        
        @Override
        public boolean confirmPhase(String transactionId) {
            System.out.println("[库存TCC] Confirm阶段 - 确认扣减");
            // 实际扣减库存
            frozenStock--;
            System.out.println("  确认扣减成功，剩余库存: " + (totalStock - frozenStock));
            return true;
        }
        
        @Override
        public boolean cancelPhase(String transactionId) {
            System.out.println("[库存TCC] Cancel阶段 - 取消预留");
            // 释放冻结的库存
            if (frozenStock > 0) {
                frozenStock--;
                System.out.println("  释放库存成功，冻结库存: " + frozenStock);
            }
            return true;
        }
    }
    
    /**
     * 账户TCC服务示例
     */
    static class AccountTccService implements TccService {
        private final long balance = 10000;
        private long frozenAmount = 0;
        
        @Override
        public boolean tryPhase(String transactionId) {
            System.out.println("[账户TCC] Try阶段 - 冻结金额");
            long amount = 100; // 扣减金额
            if (balance - frozenAmount >= amount) {
                frozenAmount += amount;
                System.out.println("  冻结金额成功，冻结金额: " + frozenAmount);
                return true;
            }
            System.out.println("  余额不足，冻结失败");
            return false;
        }
        
        @Override
        public boolean confirmPhase(String transactionId) {
            System.out.println("[账户TCC] Confirm阶段 - 确认扣减");
            frozenAmount -= 100;
            System.out.println("  确认扣减成功，冻结金额: " + frozenAmount);
            return true;
        }
        
        @Override
        public boolean cancelPhase(String transactionId) {
            System.out.println("[账户TCC] Cancel阶段 - 解冻金额");
            frozenAmount -= 100;
            if (frozenAmount < 0) frozenAmount = 0;
            System.out.println("  解冻金额成功，冻结金额: " + frozenAmount);
            return true;
        }
    }
    
    /**
     * TCC事务协调器
     */
    static class TccTransactionManager {
        private final TccService[] services;
        
        public TccTransactionManager(TccService... services) {
            this.services = services;
        }
        
        /**
         * 执行TCC事务
         */
        public boolean execute(String transactionId) {
            System.out.println("\n[TCC] 开始事务: " + transactionId);
            
            // 阶段1：Try
            boolean trySuccess = true;
            for (TccService service : services) {
                if (!service.tryPhase(transactionId)) {
                    trySuccess = false;
                    break;
                }
            }
            
            if (!trySuccess) {
                // Try失败，执行Cancel
                System.out.println("[TCC] Try阶段失败，执行Cancel");
                for (TccService service : services) {
                    service.cancelPhase(transactionId);
                }
                return false;
            }
            
            // 阶段2：Confirm
            System.out.println("[TCC] Try阶段成功，执行Confirm");
            boolean confirmSuccess = true;
            for (TccService service : services) {
                if (!service.confirmPhase(transactionId)) {
                    confirmSuccess = false;
                    // Confirm失败需要重试或人工处理
                    System.out.println("[TCC] Confirm失败，需要重试或人工处理");
                    break;
                }
            }
            
            return confirmSuccess;
        }
    }

    // ============================================================
    // 4. Saga模式
    // ============================================================
    
    /**
     * Saga分布式事务
     * 
     * 流程：
     * 1. 执行一系列本地事务
     * 2. 如果某个事务失败，执行前面所有成功事务的补偿操作
     * 
     * 两种实现方式：
     * 1. 编排式(Orchestration)：集中协调器控制流程
     * 2. 协同式(Choreography)：事件驱动，各服务自主决策
     * 
     * 优点：
     * - 无全局锁，性能好
     * - 适合长事务
     * - 实现相对简单
     * 
     * 缺点：
     * - 最终一致性，不是强一致
     * - 补偿逻辑复杂
     * - 隔离性差
     * 
     * 适用场景：跨多个服务的长事务
     */
    interface SagaStep {
        boolean execute(String sagaId);
        boolean compensate(String sagaId);
    }
    
    /**
     * Saga协调器（编排式）
     */
    static class SagaOrchestrator {
        private final java.util.List<SagaStep> steps = new java.util.ArrayList<>();
        
        public void addStep(SagaStep step) {
            steps.add(step);
        }
        
        /**
         * 执行Saga事务
         */
        public boolean execute(String sagaId) {
            System.out.println("\n[Saga] 开始事务: " + sagaId);
            
            java.util.List<SagaStep> executedSteps = new java.util.ArrayList<>();
            
            // 执行所有步骤
            for (SagaStep step : steps) {
                if (!step.execute(sagaId)) {
                    System.out.println("[Saga] 步骤执行失败，开始补偿");
                    // 反向补偿已执行的步骤
                    for (int i = executedSteps.size() - 1; i >= 0; i--) {
                        System.out.println("[Saga] 补偿步骤 " + (i + 1));
                        executedSteps.get(i).compensate(sagaId);
                    }
                    return false;
                }
                executedSteps.add(step);
            }
            
            System.out.println("[Saga] 事务执行成功");
            return true;
        }
    }
    
    /**
     * 订单Saga步骤示例
     */
    static class CreateOrderStep implements SagaStep {
        @Override
        public boolean execute(String sagaId) {
            System.out.println("  [订单Saga] 创建订单");
            return true; // 模拟成功
        }
        
        @Override
        public boolean compensate(String sagaId) {
            System.out.println("  [订单Saga] 取消订单");
            return true;
        }
    }
    
    static class DeductInventoryStep implements SagaStep {
        @Override
        public boolean execute(String sagaId) {
            System.out.println("  [库存Saga] 扣减库存");
            return true;
        }
        
        @Override
        public boolean compensate(String sagaId) {
            System.out.println "  [库存Saga] 恢复库存");
            return true;
        }
    }
    
    static class DeductBalanceStep implements SagaStep {
        @Override
        public boolean execute(String sagaId) {
            System.out.println("  [余额Saga] 扣减余额");
            return false; // 模拟失败
        }
        
        @Override
        public boolean compensate(String sagaId) {
            System.out.println("  [余额Saga] 恢复余额");
            return true;
        }
    }

    // ============================================================
    // 5. 本地消息表（最终一致性）
    // ============================================================
    
    /**
     * 本地消息表方案
     * 
     * 流程：
     * 1. 业务操作和消息写入同一事务（本地事务）
     * 2. 后台任务扫描消息表，发送到消息队列
     * 3. 消费者处理消息，处理成功后确认
     * 4. 定时任务重试未确认的消息
     * 
     * 优点：
     * - 实现简单
     * - 最终一致性
     * - 不依赖特殊中间件
     * 
     * 缺点：
     * - 与业务耦合
     * - 消息表扫描有性能开销
     * 
     * 适用场景：对一致性要求不高的场景
     */
    static class LocalMessageTable {
        private final java.util.List<String> pendingMessages = new java.util.ArrayList<>();
        private final java.util.List<String> processedMessages = new java.util.ArrayList<>();
        
        /**
         * 业务操作 + 写入消息表（同一事务）
         */
        public boolean businessWithMessage(String businessData) {
            System.out.println("\n[本地消息表] 执行业务操作");
            
            // 1. 执行业务操作（模拟）
            System.out.println("  执行业务: " + businessData);
            
            // 2. 写入消息表（同一本地事务）
            String message = "msg:" + System.currentTimeMillis();
            pendingMessages.add(message);
            System.out.println("  写入消息: " + message);
            
            return true;
        }
        
        /**
         * 后台任务：发送消息
         */
        public void sendMessageTask() {
            System.out.println("[本地消息表] 扫描并发送消息");
            
            for (String message : new java.util.ArrayList<>(pendingMessages)) {
                try {
                    // 发送到消息队列（模拟）
                    System.out.println("  发送消息: " + message);
                    
                    // 发送成功，标记为已处理
                    pendingMessages.remove(message);
                    processedMessages.add(message);
                } catch (Exception e) {
                    System.out.println("  发送失败: " + message + ", 稍后重试");
                }
            }
        }
        
        /**
         * 定时任务：重试失败的消息
         */
        public void retryTask() {
            System.out.println("[本地消息表] 重试未处理消息");
            System.out.println("  待处理消息数: " + pendingMessages.size());
        }
    }

    // ============================================================
    // 演示和测试
    // ============================================================
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      分布式事务面试题详解 - 高级工程师必备        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        // 1. 2PC演示
        System.out.println("\n【1. 两阶段提交 (2PC)】");
        TwoPhaseCommit twoPC = new TwoPhaseCommit(3);
        if (twoPC.prepare()) {
            twoPC.commit();
        } else {
            twoPC.rollback();
        }
        
        // 2. 3PC演示
        System.out.println("\n【2. 三阶段提交 (3PC)】");
        ThreePhaseCommit threePC = new ThreePhaseCommit();
        threePC.execute();
        
        // 3. TCC演示
        System.out.println("\n【3. TCC事务】");
        TccTransactionManager tccManager = new TccTransactionManager(
            new InventoryTccService(),
            new AccountTccService()
        );
        tccManager.execute("tx-001");
        
        // 4. Saga演示
        System.out.println("\n【4. Saga事务】");
        SagaOrchestrator saga = new SagaOrchestrator();
        saga.addStep(new CreateOrderStep());
        saga.addStep(new DeductInventoryStep());
        saga.addStep(new DeductBalanceStep());
        saga.execute("saga-001");
        
        // 5. 本地消息表演示
        System.out.println("\n【5. 本地消息表】");
        LocalMessageTable messageTable = new LocalMessageTable();
        messageTable.businessWithMessage("用户下单");
        messageTable.sendMessageTask();
        messageTable.retryTask();
        
        // 面试总结
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║           分布式事务面试总结                     ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        System.out.println("\n【方案对比】");
        System.out.println("  方案     | 一致性   | 性能 | 复杂度 | 适用场景");
        System.out.println("  ---------|----------|------|--------|----------");
        System.out.println("  2PC      | 强一致   | 低   | 中等   | 数据库事务");
        System.out.println("  3PC      | 强一致   | 中   | 高     | 改进2PC");
        System.out.println("  TCC      | 强一致   | 中   | 高     | 核心业务");
        System.out.println("  Saga     | 最终一致 | 高   | 中等   | 长事务");
        System.out.println("  本地消息表 | 最终一致 | 高   | 低     | 普通业务");
        
        System.out.println("\n【选择建议】");
        System.out.println("  - 数据库层面：2PC/3PC（如跨库事务）");
        System.out.println("  - 核心业务：TCC（如资金、库存）");
        System.out.println("  - 跨服务长事务：Saga（如电商下单流程）");
        System.out.println("  - 普通业务：本地消息表/事务消息");
        
        System.out.println("\n【面试高频问题】");
        System.out.println("  Q1: TCC的空回滚问题？");
        System.out.println("      A: Try未执行但Cancel执行了，需要记录事务状态");
        System.out.println("  Q2: TCC的悬挂问题？");
        System.out.println("      A: Cancel比Try先执行，需要防悬挂机制");
        System.out.println("  Q3: Saga的隔离性问题？");
        System.out.println("      A: Saga没有隔离性，需要业务层面处理脏读");
    }
}
