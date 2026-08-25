package org.wang.rabbitmqlab.common;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;       // 用于创建连接的工厂类

import java.io.IOException;                          // IO 异常（网络/Broker 不可达）
import java.util.concurrent.TimeoutException;        // 连接/操作超时异常

/**
 * RabbitMQ 连接工具类（全 lab 共用）
 *
 * 目的：把 Broker 连接参数集中管理，避免每个 demo 重复硬编码 host/port/账号/虚拟主机。
 *      以后改 Broker 地址或密码，只改这一处，21 个 demo 全部生效。
 *
 * 用法（替换原来每个 demo 里的 6 行工厂样板代码）：
 *   try (Connection conn = ConnectionUtil.createConnection();
 *        Channel ch = conn.createChannel()) { ... }
 *
 * 说明：
 *  - 这是【学习 lab】，连接参数直接写死成常量最简单，符合现有风格。
 *  - 真实项目不要这样：应把账号密码放配置文件 / 环境变量 / 配置中心，不入库、不进 git。
 *  - 本类故意只做「创建连接」一件事，不掺杂任何业务逻辑，保持纯净。
 *
 * @author wang
 * @date 2026-08-16
 */
public final class ConnectionUtil {

    // 私有构造：工具类禁止实例化（只能通过静态方法使用）
    private ConnectionUtil() {
    }

    // ============================ Broker 连接参数 ============================
    public static final String HOST = "192.168.6.132";      // Broker 的 IP 地址
    public static final int PORT = 5672;                    // Broker 的 AMQP 端口
    public static final String USERNAME = "admin";          // 登录用户名
    public static final String PASSWORD = "passw0rd";       // 登录密码
    public static final String VIRTUAL_HOST = "/mirror";    // 虚拟主机

    /**
     * 创建并返回一个 RabbitMQ 连接（含 TCP 握手 + AMQP 协议握手）
     */
    public static Connection createConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);
        factory.setVirtualHost(VIRTUAL_HOST);
        return factory.newConnection();
    }
}
