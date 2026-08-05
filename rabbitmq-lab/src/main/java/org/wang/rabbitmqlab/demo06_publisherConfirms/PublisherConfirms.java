package org.wang.rabbitmqlab.demo06_publisherConfirms;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.ConfirmCallback;        // 确认回调接口（ack/nack 时触发）
import com.rabbitmq.client.Connection;             // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;      // 用于创建连接的工厂类

import java.time.Duration;                         // 时间间隔工具类（统计耗时）
import java.util.LinkedList;                       // 链表（自适应限流模式用的待确认集合）
import java.util.UUID;                             // 生成唯一随机数（用作临时队列名）
import java.util.concurrent.ConcurrentNavigableMap;// 线程安全的可导航 Map（按键排序）
import java.util.concurrent.ConcurrentSkipListMap; // 跳表实现的并发有序 Map（默认按序号升序）
import java.util.function.BooleanSupplier;         // 布尔供应函数（无参返回 boolean）

/**
 * Demo06 - 发布者确认（Publisher Confirms）
 * 目的：确保消息【真正到达了 Broker】，防止发送过程中消息丢失
 *
 * 原理：生产者开启 confirm 模式后，Broker 会对每条消息回一个确认
 *      basic.ack  = 正向确认，表示 Broker 已接管该消息
 *                   （对持久化消息而言 = 已写入磁盘）
 *      basic.nack = 负向确认，仅当 Broker 内部进程出错时才发生
 *
 * 注意：官方文档指出确认可能【乱序到达】，应用不应依赖确认顺序
 *
 * 本类演示 4 种确认策略，并对比它们的吞吐量：
 *   1. 单独确认（publishMessagesIndividually）—— 最安全最慢
 *   2. 批量确认（publishMessagesInBatch）       —— 折中方案
 *   3. 异步确认（handlePublishConfirmsAsynchronously）—— 回调处理，不阻塞
 *   4. 确认窗口 + 自适应限流（handlePublishConfirmsWithWindow / AdaptiveThrottling）—— 最优方案
 */
public class PublisherConfirms {

    // 总发送消息数：5 万条，用于测试吞吐量
    static final int MESSAGE_COUNT = 50_000;

    // 确认窗口大小：最多允许 1000 条消息未确认（在途消息）
    static final int MAX_OUTSTANDING = 1000;
    // 限流百分比：当在途消息达到窗口的 50% 时，开始缓慢发送
    static final int THROTTLING_PERCENTAGE = 50;
    // 最大延迟：限流时单次最多睡 100ms
    static final int MAX_DELAY_MS = 100;

    /**
     * 创建并返回一个 RabbitMQ 连接
     */
    static Connection createConnection() throws Exception {
        // 1. 创建连接工厂，配置 Broker 连接信息
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");           // Broker 的 IP 地址
        factory.setPort(5672);                      // Broker 的 AMQP 端口
        factory.setUsername("admin");               // 登录用户名
        factory.setPassword("passw0rd");            // 登录密码
        factory.setVirtualHost("/mirror");          // 虚拟主机

        // 2. 返回新连接（这里有 TCP 握手 + AMQP 协议握手）
        return factory.newConnection();
    }

    public static void main(String[] args) throws Exception {
        publishMessagesIndividually();
        publishMessagesInBatch();
        handlePublishConfirmsAsynchronously();
        handlePublishConfirmsWithWindow();
        handlePublishConfirmsWithAdaptiveThrottling();
    }

    /**
     * 方式一：单独确认（逐条等待）
     * 每发一条消息，就阻塞等待 Broker 确认，确认后再发下一条
     */
    static void publishMessagesIndividually() throws Exception {
        try (Connection connection = createConnection()) {
            // 创建信道
            Channel channel = connection.createChannel();

            // 开启发布者确认模式（confirm 模式）
            // 不开启则 basicPublish 是"发完即忘"，Broker 不回报
            channel.confirmSelect();

            // 生成一个唯一临时队列名，避免和其他运行实例冲突
            String queue = UUID.randomUUID().toString();
            // 声明临时队列：非持久化、非排他、自动删除
            channel.queueDeclare(queue, false, false, true, null);

            // 记录开始时间（nanoTime 高精度计时）
            long start = System.nanoTime();

            // 循环发送 5 万条消息
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);                // 消息体就是数字本身
                channel.basicPublish("", queue, null, body.getBytes()); // 发到默认 exchange，路由到队列
                // 阻塞等待这条消息的确认，最多 5 秒
                // 收到 nack 或超时都会抛异常（waitForConfirmsOrDie = 等不到就死）
                // 缺点：每发一条就阻塞等一次 RTT，性能最差
                channel.waitForConfirmsOrDie(5000);
            }

            // 记录结束时间
            long end = System.nanoTime();
            // 打印耗时统计（Duration.ofNanos 把纳秒转毫秒）
            System.out.format("Published %,d messages individually in %,d ms%n",
                    MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    /**
     * 方式二：批量确认
     * 发满一批（100 条）才等一次确认，减少等待次数，吞吐更高
     */
    static void publishMessagesInBatch() throws Exception {
        try (Connection connection = createConnection()) {
            Channel channel = connection.createChannel();

            channel.confirmSelect();                            // 开启 confirm 模式

            String queue = UUID.randomUUID().toString();
            channel.queueDeclare(queue, false, false, true, null);

            int batchSize = 100;                                // 每批 100 条
            int outstandingMessageCount = 0;                    // 当前这批还没确认的数量

            long start = System.nanoTime();

            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);
                channel.basicPublish("", queue, null, body.getBytes());

                outstandingMessageCount++;                      // 在途消息 +1
                if (outstandingMessageCount == batchSize) {     // 攒满一批了
                    channel.waitForConfirmsOrDie(5000);         // 等这一批全部确认
                    outstandingMessageCount = 0;                // 重置计数
                }
            }

            // 最后一批可能没凑满 100 条，单独确认一次
            if (outstandingMessageCount > 0) {
                channel.waitForConfirmsOrDie(5000);
            }

            long end = System.nanoTime();
            System.out.format("Published %,d messages in batch in %,d ms%n",
                    MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    /**
     * 方式三：异步确认（回调方式，不阻塞）
     * 发消息完全不等待，Broker 确认后通过【回调】通知处理
     * 需要自己维护"哪些消息还没确认"的集合（用序号做 key）
     */
    static void handlePublishConfirmsAsynchronously() throws Exception {
        try (Connection connection = createConnection()) {
            Channel ch = connection.createChannel();

            String queue = UUID.randomUUID().toString();
            ch.queueDeclare(queue, false, false, true, null);

            ch.confirmSelect();                                 // 开启 confirm 模式

            // 在途确认表：<消息序号, 消息体>
            // 序号是确认（ack/nack）里 delivery-tag 字段携带的值
            // 官方文档：Broker 和客户端【各自】对发布的消息计数（从 1 开始），
            //           delivery-tag 就是序号，用来把两边的计数对上
            // 用跳表实现（ConcurrentSkipListMap），线程安全且按键升序排列
            // 好处：多条消息一起确认（multiple=true）时，可以快速取出所有 <= 该序号的条目
            // 注意：确认可能乱序到达，所以不能依赖"按发送顺序逐个对账"
            ConcurrentNavigableMap<Long, String> outstandingConfirms = new ConcurrentSkipListMap<>();

            // ack 清理回调：Broker 确认某条（或某段）消息后，从在途表中移除
            ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
                if (multiple) {
                    // multiple=true：确认了该序号及之前的所有消息
                    // headMap(seq, true)：取出 <= seq 的所有条目（含 seq 本身）
                    ConcurrentNavigableMap<Long, String> confirmed = outstandingConfirms.headMap(
                            sequenceNumber, true
                    );
                    confirmed.clear();                          // 一次性清空这些已确认的消息
                } else {
                    // multiple=false：只确认了这一条，直接按序号移除
                    outstandingConfirms.remove(sequenceNumber);
                }
            };

            // 注册确认监听器（异步线程回调）
            // 参数1：ack 回调 —— 确认成功，清理在途表
            // 参数2：nack 回调 —— 确认失败（仅当 Broker 内部进程出错时发生）
            ch.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
                // 打印失败的消息内容，方便排查
                String body = outstandingConfirms.get(sequenceNumber);
                System.err.format(
                        "Message with body %s has been nack-ed. Sequence number: %d, multiple: %b%n",
                        body, sequenceNumber, multiple
                );
                // 失败也照常清理，否则在途表会一直累积
                cleanOutstandingConfirms.handle(sequenceNumber, multiple);
            });

            long start = System.nanoTime();

            // 狂发 5 万条，完全不等待
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);
                // 先记录序号（getNextPublishSeqNo 返回【客户端这边】下一条消息的序号）
                // 官方文档：Broker 和客户端各自计数（从 1 开始），
                //          delivery-tag 里的序号把两边的计数对上
                // 必须在 basicPublish 之前获取：客户端计数器在 publish 时会 +1
                // 先领号 → 记在途表 → 再发消息，三者必须配对
                outstandingConfirms.put(ch.getNextPublishSeqNo(), body);
                ch.basicPublish("", queue, null, body.getBytes());
            }

            // 等待在途表清空（即所有消息都被确认）
            // 最多等 60 秒，超时抛异常
            if (!waitUntil(Duration.ofSeconds(60), outstandingConfirms::isEmpty)) {
                throw new IllegalStateException("All messages could not be confirmed in 60 seconds");
            }

            long end = System.nanoTime();
            System.out.format("Published %,d messages and handled confirms asynchronously in %,d ms%n",
                    MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    /**
     * 方式四（a）：确认窗口 + 阻塞等待
     * 在异步确认基础上增加【窗口限制】：在途消息最多 1000 条
     * 满了就阻塞等待确认，防止一次发太多撑爆 Broker
     */
    static void handlePublishConfirmsWithWindow() throws Exception {
        try (Connection connection = createConnection()) {
            Channel ch = connection.createChannel();

            String queue = UUID.randomUUID().toString();
            ch.queueDeclare(queue, false, false, true, null);
            ch.confirmSelect();                                 // 开启 confirm 模式

            // 在途确认表（同方式三）
            ConcurrentNavigableMap<Long, String> outstandingConfirms = new ConcurrentSkipListMap<>();

            // ack 清理回调：和方式三一样，多了一条【唤醒等待线程】
            ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
                if (multiple) {
                    outstandingConfirms.headMap(sequenceNumber, true).clear(); // 批量清空
                } else {
                    outstandingConfirms.remove(sequenceNumber);               // 单条移除
                }
                // 关键：通知可能在 wait 的生产线程"有空位了"，让它继续发
                synchronized (outstandingConfirms) {
                    outstandingConfirms.notifyAll();
                }
            };

            ch.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
                System.err.format("Message nacked. Sequence: %d, multiple: %b%n", sequenceNumber, multiple);
                cleanOutstandingConfirms.handle(sequenceNumber, multiple);
            });

            long start = System.nanoTime();

            for (int i = 0; i < MESSAGE_COUNT; i++) {
                // 窗口满了就阻塞等待
                // wait() 会释放锁，等 ack 回调 notifyAll 唤醒
                synchronized (outstandingConfirms) {
                    while (outstandingConfirms.size() >= MAX_OUTSTANDING) {
                        outstandingConfirms.wait();
                    }
                }

                String body = String.valueOf(i);
                outstandingConfirms.put(ch.getNextPublishSeqNo(), body);
                ch.basicPublish("", queue, null, body.getBytes());
            }

            // 所有消息发完后，再等最后的确认（在途表清空）
            synchronized (outstandingConfirms) {
                while (!outstandingConfirms.isEmpty()) {
                    outstandingConfirms.wait();
                }
            }

            long end = System.nanoTime();
            System.out.format("Published %,d messages with confirmation window in %,d ms%n",
                    MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    /**
     * 方式四（b）：确认窗口 + 自适应限流（最优方案）
     * 在窗口基础上进一步优化：当窗口快满时【渐进式放慢速度】
     * 而不是满了一刀切地阻塞，避免吞吐量突然跌到 0
     */
    static void handlePublishConfirmsWithAdaptiveThrottling() throws Exception {
        try (Connection connection = createConnection()) {
            Channel ch = connection.createChannel();

            String queue = UUID.randomUUID().toString();
            ch.queueDeclare(queue, false, false, true, null);
            ch.confirmSelect();                                 // 开启 confirm 模式

            // 在途序号列表（这里用 LinkedList 保存序号即可，不需要消息体）
            LinkedList<Long> outstandingConfirms = new LinkedList<>();
            // 限流阈值：1000 * 50% = 500，即在途达到 500 时开始放缓
            int throttlingThreshold = MAX_OUTSTANDING * THROTTLING_PERCENTAGE / 100;

            // ack 清理回调
            ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
                synchronized (outstandingConfirms) {
                    if (multiple) {
                        // 批量确认：移除所有 <= seq 的序号
                        outstandingConfirms.removeIf(seqNo -> seqNo <= sequenceNumber);
                    } else {
                        // 单条确认：移除该序号
                        outstandingConfirms.removeFirstOccurrence(sequenceNumber);
                    }
                    outstandingConfirms.notifyAll();            // 唤醒可能等待的生产线程
                }
            };

            ch.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
                System.err.format("Message nacked. Sequence: %d, multiple: %b%n", sequenceNumber, multiple);
                cleanOutstandingConfirms.handle(sequenceNumber, multiple);
            });

            long start = System.nanoTime();

            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);

                synchronized (outstandingConfirms) {
                    // 窗口满了：彻底阻塞等待（最坏情况）
                    while (outstandingConfirms.size() >= MAX_OUTSTANDING) {
                        outstandingConfirms.wait();
                    }

                    // 可用配额 = 窗口大小 - 当前在途数量
                    int availablePermits = MAX_OUTSTANDING - outstandingConfirms.size();
                    // 如果可用配额小于阈值（即窗口用掉超过 50%），开始限流
                    if (availablePermits < throttlingThreshold) {
                        // 计算窗口使用率：1 - 剩余/总 = 已用比例（如 0.6 = 用了 60%）
                        double percentageUsed = 1.0 - (availablePermits / (double) MAX_OUTSTANDING);
                        // 根据使用率算延迟：用越多，睡越久（0 ~ 100ms）
                        int delay = (int) (percentageUsed * MAX_DELAY_MS);
                        if (delay > 0) {
                            outstandingConfirms.wait(delay);    // 睡 delay 毫秒，放缓节奏
                        }
                    }

                    // 记录序号进在途列表（wait 后重新检查条件，防虚假唤醒）
                    long seqNo = ch.getNextPublishSeqNo();
                    outstandingConfirms.addLast(seqNo);
                }

                ch.basicPublish("", queue, null, body.getBytes());
            }

            // 等最后一批确认完
            synchronized (outstandingConfirms) {
                while (!outstandingConfirms.isEmpty()) {
                    outstandingConfirms.wait();
                }
            }

            long end = System.nanoTime();
            System.out.format("Published %,d messages with adaptive throttling in %,d ms%n",
                    MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    /**
     * 轮询等待工具方法：每隔 100ms 检查一次条件，直到满足或超时
     */
    static boolean waitUntil(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        int waited = 0;                                        // 已等待的毫秒数
        // 条件不满足且没超时，就继续轮询
        while (!condition.getAsBoolean() && waited < timeout.toMillis()) {
            Thread.sleep(100L);                                // 每 100ms 检查一次
            waited += 100;
        }
        // 返回最终条件是否满足（超时返回 false）
        return condition.getAsBoolean();
    }
}
