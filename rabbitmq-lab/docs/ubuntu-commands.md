# Ubuntu 上 RabbitMQ 运维命令合集

> 速查手册。按场景分组，覆盖安装、服务管理、状态诊断、用户权限、队列、策略、插件、管理控制台、日志配置、集群。
> 对应本 lab 的 Broker 地址：`192.168.6.132`，vhost `/mirror`，用户 `admin`。

---

## 一、安装（apt 官方源）

```bash
# 1. 安装 Erlang（RabbitMQ 依赖，必须先装）
# 官方推荐用 RabbitMQ 的 Erlang 包，避免 Ubuntu 自带版本太旧
curl -fsSL https://github.com/rabbitmq/signing-keys/releases/download/2.0/rabbitmq-release-signing-key.asc | sudo gpg --dearmor -o /usr/share/keyrings/rabbitmq.gpg
echo "deb [signed-by=/usr/share/keyrings/rabbitmq.gpg] https://ppa.launchpadcontent.net/rabbitmq/rabbitmq-erlang/ubuntu $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/rabbitmq-erlang.list

# 2. 安装 RabbitMQ 服务端
echo "deb [signed-by=/usr/share/keyrings/rabbitmq.gpg] https://ppa.launchpadcontent.net/rabbitmq/rabbitmq-server/ubuntu $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/rabbitmq.list

sudo apt-get update
sudo apt-get install -y erlang-base erlang-asn1 erlang-crypto erlang-eldap erlang-inets \
    erlang-mnesia erlang-os-mon erlang-public-key erlang-ssl erlang-syntax-tools \
    rabbitmq-server
```

> Docker 用户跳过上面，直接：
> `docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management`

---

## 二、服务管理

```bash
# 启动 / 停止 / 重启（systemd 管的，开机自启）
sudo systemctl start rabbitmq-server
sudo systemctl stop rabbitmq-server
sudo systemctl restart rabbitmq-server
sudo systemctl status rabbitmq-server
sudo systemctl enable rabbitmq-server     # 开机自启
sudo systemctl disable rabbitmq-server    # 关闭自启

# 前台运行（调试用，看实时日志）
sudo rabbitmq-server

# detached 后台运行（不走 systemd）
sudo rabbitmq-server -detached
```

---

## 三、状态与诊断

```bash
# 总览状态（最常用：节点名、运行时长、内存、磁盘、连接数、队列数、Erlang 版本）
sudo rabbitmqctl status

# 集群状态（单节点也能看，显示节点角色）
sudo rabbitmqctl cluster_status

# 节点运行环境健康检查（OOM 预警、磁盘告警等）
sudo rabbitmq-diagnostics server_version
sudo rabbitmq-diagnostics check_running
sudo rabbitmq-diagnostics check_port_connectivity
sudo rabbitmq-diagnostics check_port_listener
sudo rabbitmq-diagnostics ping

# 资源告警查看（内存、磁盘水位线）
sudo rabbitmqctl status | grep -A5 "Memory"
sudo rabbitmq-diagnostics memory_breakdown      # 内存按类别细分
sudo rabbitmq-diagnostics disk_free             # 剩余磁盘

# 各资源计数（连接 / 通道 / 队列 / 消费者 / 交换机）
sudo rabbitmqctl list_connections
sudo rabbitmqctl list_channels
sudo rabbitmqctl list_queues
sudo rabbitmqctl list_consumers
sudo rabbitmqctl list_exchanges
sudo rabbitmqctl list_bindings
sudo rabbitmqctl list_vhosts
sudo rabbitmqctl list_users

# 列出队列带详细信息（name、messages、consumers、state）
sudo rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers state
```

---

## 四、用户与权限

```bash
# 用户管理
sudo rabbitmqctl add_user admin passw0rd           # 建用户
sudo rabbitmqctl set_user_tags admin administrator # 设标签（administrator 是管理控制台权限）
sudo rabbitmqctl list_users
sudo rabbitmqctl delete_user guest                 # 删默认 guest（生产建议删）
sudo rabbitmqctl change_password admin newpass

# 虚拟主机（vhost，逻辑隔离）
sudo rabbitmqctl add_vhost /mirror
sudo rabbitmqctl list_vhosts
sudo rabbitmqctl delete_vhost /mirror

# 权限：vhost 下给用户配置 / 写 / 读权限
# set_permissions [-p vhost] <user> <conf> <write> <read>
#   ".*" 表示匹配所有资源
sudo rabbitmqctl set_permissions -p /mirror admin ".*" ".*" ".*"
sudo rabbitmqctl list_permissions -p /mirror
sudo rabbitmqctl clear_permissions -p /mirror admin

# Topic 权限（更细粒度，按 routing key 控制，5.x+）
sudo rabbitmqctl set_topic_permissions -p /mirror admin "^order\." "^order\." "^order\."
```

---

## 五、队列操作

```bash
# 查看某队列详情（参数、消费者、消息数）
sudo rabbitmqctl list_queues name arguments consumers messages

# 清空队列消息（不删队列，只清消息）
sudo rabbitmqctl purge_queue order.delay.queue

# 删除队列（管理控制台也能删，命令行更直接）
sudo rabbitmqctl delete_queue order.delay.queue
# 或用 rabbitmqadmin（见下方第十节）

# 手动同步镜像队列（镜像队列场景）
sudo rabbitmqctl sync_queue order.delay.queue
sudo rabbitmqctl cancel_sync_queue order.delay.queue
```

---

## 六、策略（Policy）—— 运维最常用

```bash
# 策略给队列/交换机批量挂参数（如改 TTL、镜像、DLX 等）
# set_policy [-p vhost] [--priority <int>] [--apply-to <type>] <name> <pattern> <definition>
sudo rabbitmqctl set_policy -p /mirror --apply-to queues \
    "ttl-policy" "^order\." '{"message-ttl": 1800000}'

# 常用策略示例：
# 镜像队列（经典镜像，3.x；4.x 用 Quorum Queue 代替）
sudo rabbitmqctl set_policy ha-all "^ha\." \
    '{"ha-mode":"all","ha-sync-mode":"automatic"}'

# 队列 TTL
sudo rabbitmqctl set_policy --apply-to queues "q-ttl" "^temp\." '{"message-ttl":60000}'

# 队列最大长度
sudo rabbitmqctl set_policy --apply-to queues "max-len" "^order\." '{"max-length":1000}'

# 死信（DLX）
sudo rabbitmqctl set_policy --apply-to queues "dlx" "^biz\." \
    '{"dead-letter-exchange":"dlx.exchange","dead-letter-routing-key":"dlx"}'

# 查看策略
sudo rabbitmqctl list_policies -p /mirror

# 清除策略
sudo rabbitmqctl clear_policy "ttl-policy" -p /mirror
```

---

## 七、插件管理

> 本 lab 的 demo13（`rabbitmq_delayed_message_exchange`）依赖延迟插件，见第八节单独装。

```bash
# 列出所有插件及启用状态
sudo rabbitmq-plugins list

# 启用 / 禁用插件（enable 后会自动解决依赖）
sudo rabbitmq-plugins enable rabbitmq_management          # 管理控制台（Web UI，15672）
sudo rabbitmq-plugins enable rabbitmq_delayed_message_exchange  # 延迟消息交换机（demo13 要装）
sudo rabbitmq-plugins enable rabbitmq_shovel              # 跨节点/跨 broker 消息搬运
sudo rabbitmq-plugins enable rabbitmq_federation          # 联邦（跨 broker 路由）
sudo rabbitmq-plugins enable rabbitmq_stream_management   # Stream 管理
sudo rabbitmq-plugins enable rabbitmq_prometheus          # Prometheus 指标

sudo rabbitmq-plugins disable rabbitmq_management

# 装完后重启服务（部分插件需要）
sudo systemctl restart rabbitmq-server

# 查看已启用插件
sudo rabbitmq-plugins list -e
```

---

## 八、延迟插件安装（demo13 必装）

延迟插件不在官方仓库，要单独下 `.ez` 文件放插件目录：

```bash
# 1. 下载（去 GitHub rabbitmq/rabbitmq-delayed-message-exchange releases 找对应版本）
#    版本要匹配 RabbitMQ 主版本（3.13 / 4.0 各有对应 release）
sudo wget -P /usr/lib/rabbitmq/plugins/ \
    https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/3.13.0/rabbitmq_delayed_message_exchange-3.13.0.ez

# 2. 启用
sudo rabbitmq-plugins enable rabbitmq_delayed_message_exchange

# 3. 重启
sudo systemctl restart rabbitmq-server

# 4. 验证：管理控制台 exchange type 下拉应多出 x-delayed-message
#    或命令行查
sudo rabbitmq-plugins list -e | grep delayed
```

装完后跑本 lab 的 demo13：
```
DelayPluginTopology → Consumer → Producer
```

---

## 九、管理控制台（Web UI）

```bash
# 启用插件后访问 http://<host>:15672，默认 guest/guest（只能本机登录）
# 远程登录要建 administrator 标签用户（见第四节）

# 端口对照：
#   5672  AMQP 协议端口（应用连这个）
#   15672 管理控制台
#   25672 集群节点通信
#   61613 STOMP / 1883 MQTT（需对应插件）
```

---

## 十、rabbitmqadmin（命令行管理 API 客户端）

比 `rabbitmqctl` 更适合操作队列/交换机/消息，装了管理插件后自带：

```bash
# 下载（管理控制台首页有下载链接，或）
wget http://localhost:15672/cli/rabbitmqadmin
chmod +x rabbitmqadmin
sudo mv rabbitmqadmin /usr/local/bin/

# 常用操作
rabbitmqadmin -V /mirror -u admin -p passw0rd list queues
rabbitmqadmin -V /mirror -u admin -p passw0rd list exchanges
rabbitmqadmin -V /mirror -u admin -p passw0rd delete queue name=order.delay.queue
rabbitmqadmin -V /mirror -u admin -p passw0rd purge queue name=order.delay.queue
rabbitmqadmin -V /mirror -u admin -p passw0rd publish \
    exchange=amq.default routing_key=hello payload="hello" properties='{"delivery_mode":2}'
rabbitmqadmin -V /mirror -u admin -p passw0rd get queue=hello ackmode=ack_requeue_false count=1
```

---

## 十一、日志与配置

```bash
# 日志位置（systemd 下）
ls /var/log/rabbitmq/
#   rabbit@<hostname>.log        主日志
#   rabbit@<hostname>_upgrade.log 升级日志

# 实时看日志
sudo tail -f /var/log/rabbitmq/rabbit@*.log

# 配置文件
#   /etc/rabbitmq/rabbitmq.conf      新格式（recommended）
#   /etc/rabbitmq/advanced.config    高级配置（Erlang term 格式）
#   /etc/rabbitmq/rabbitmq-env.conf  环境变量（端口、数据目录等）

# 常用配置（rabbitmq.conf）：
#   listeners.tcp.default = 5672
#   management.tcp.port = 15672
#   loopback_users.guest = false      允许 guest 远程登录（仅测试）
#   vm_memory_high_watermark.relative = 0.6    内存水位线
#   disk_free_limit.absolute = 2GB             磁盘告警阈值

# 改完配置重启
sudo systemctl restart rabbitmq-server

# 验证配置语法（启动前检查）
sudo rabbitmq-server --validate-config
```

---

## 十二、数据目录与卸载

```bash
# 数据目录（Mnesia 数据库、消息存储）
#   /var/lib/rabbitmq/mnesia/
ls /var/lib/rabbitmq/mnesia/rabbit@<hostname>/

# 卸载（保留数据）
sudo apt-get remove rabbitmq-server
# 彻底卸载（连数据一起删，谨慎！）
sudo apt-get purge rabbitmq-server erlang-*
sudo rm -rf /var/lib/rabbitmq /etc/rabbitmq /var/log/rabbitmq
```

---

## 十三、集群（单节点不用，备用）

```bash
# 节点加入集群（两个节点都要先停 app）
sudo rabbitmqctl stop_app
sudo rabbitmqctl join_cluster rabbit@node1    # 在 node2 上执行，加入 node1
sudo rabbitmqctl start_app

# 退出集群
sudo rabbitmqctl stop_app
sudo rabbitmqctl reset                # 清空本节点数据
sudo rabbitmqctl start_app

# 查看集群状态
sudo rabbitmqctl cluster_status
```
