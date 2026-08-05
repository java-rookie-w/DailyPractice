package org.wang.rabbitmqlab.demo07_headers;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道,所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口,收到消息时触发

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码,用于消息体转换
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.wang.rabbitmqlab.demo07_headers.EmitLogHeader.EXCHANGE_NAME;

/**
 * Demo07 - Headers Exchange 消费者 1(x-match = all)
 *
 * 职责:创建临时队列绑定到 headers exchange,使用 x-match=all 匹配策略
 *
 * ============================================================
 *  x-match = all 含义:
 *  ============================================================
 *  消息的 headers 中必须包含绑定中指定的所有键值对,才能投递到此队列
 *  (AND 逻辑:所有条件都要满足)
 *
 * ============================================================
 *  本消费者绑定的 header 条件:
 *  ============================================================
 *  level = error
 *  source = db
 *
 *  匹配结果预测(基于 EmitLogHeader 发送的 6 条消息):
 *  ============================================================
 *  消息内容                                    headers                        是否收到
 *  "数据库连接失败:连接超时"                   {level=error, source=db}        ✅  匹配
 *  "用户认证失败:token 已过期"                  {level=error, source=auth}     ❌  source≠db
 *  "数据库连接池使用率超过 80%"                 {level=warning, source=db}     ❌  level≠error
 *  "用户登录成功:admin"                         {level=info, source=api}       ❌  都不匹配
 *  "系统核心模块异常:内存溢出"                  {level=critical}               ❌  缺少 source=db
 *  "{\"code\":500,\"msg\":\"DB timeout\"}"     {level=error, source=db, format=json} ✅ 匹配(额外 header 不影响)
 *
 *  → 本消费者收到 2 条消息(消息 1 和消息 6)
 *
 * ============================================================
 *
 * @author wang
 * @date 2026-08-04
 */
public class ReceiveLogsHeaderAll {

    static void main() throws IOException, TimeoutException {

        System.out.println("ReceiveLogsHeaderAll(x-match = all)");

        // 1. 创建连接工厂,配置 Broker 的连接信息
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");          // Broker 的 IP 地址
        factory.setPort(5672);                     // Broker 的 AMQP 端口
        factory.setUsername("admin");              // 登录用户名
        factory.setPassword("passw0rd");           // 登录密码
        factory.setVirtualHost("/mirror");         // 虚拟主机

        // 2. 建立连接和信道(不用 try-with-resources,basicConsume 需要保持连接)
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // 3. 声明 exchange(和 EmitLogHeader 一样,确保 exchange 存在)
        //    类型为 headers,与生产者保持一致
        channel.exchangeDeclare(EXCHANGE_NAME, "headers");

        // 4. 声明一个临时队列(由 Broker 自动生成唯一名称)
        String queueName = channel.queueDeclare().getQueue();

        // ============================================================
        // 5. 把临时队列绑定到 exchange,设置 header 匹配条件
        // ============================================================
        // 绑定参数:
        //   x-match = "all"  → 所有指定 header 都必须匹配
        //   level   = "error" → 要求消息的 level header 为 "error"
        //   source  = "db"    → 要求消息的 source header 为 "db"
        //
        // 注意:binding key 传空字符串,headers exchange 不依赖 routing key
        // header 匹配条件通过 arguments(Map) 传入 queueBind 的最后一个参数
        Map<String, Object> bindArgs = new HashMap<>();
        bindArgs.put("x-match", "all");
        bindArgs.put("level", "error");
        bindArgs.put("source", "db");

        channel.queueBind(queueName, EXCHANGE_NAME, "", bindArgs);

        // 6. 打印等待提示
        System.out.println(" [*] Waiting for messages with level=error AND source=db. To exit press CTRL+C");

        // ============================================================
        // 7. 定义消息投递回调,当 Broker 推送消息时自动执行
        // ============================================================
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 从 delivery 中取出消息体,转为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            // 获取消息的 headers(从消息属性中获取)
            Map<String, Object> headers = delivery.getProperties().getHeaders();

            // 打印接收到的消息及其 headers
            System.out.println(" [x] Received '" + message + "'");
            System.out.println("     headers: " + headers);
        };

        // ============================================================
        // 8. 开始消费消息(长订阅模式)
        // ============================================================
        // 参数:队列名, autoAck=true(自动确认), 消息回调, 取消回调
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
    }
}