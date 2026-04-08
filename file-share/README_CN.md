# P2P File Share Application

一个简洁的 P2P 文件共享应用，支持手机和电脑之间直接传输文件。

## 🚀 快速启动

### 方式一：使用 Maven 命令
```bash
cd file-share
mvn spring-boot:run
```

### 方式二：运行打包后的 JAR
```bash
java -jar target/DailyPractice.jar
```

## 📱 使用方法

1. **启动应用**后，在浏览器访问：`http://localhost:8081`

2. **上传文件**：
   - 点击"选择文件"按钮
   - 或直接拖拽文件到上传区域

3. **下载文件**：
   - 在文件列表中点击"Download"按钮

4. **删除文件**：
   - 点击文件旁边的"Delete"按钮

## 🔧 配置说明

- **Web 端口**: 8081 (浏览器访问)
- **P2P 传输端口**: 8082 (文件传输)
- **文件存储位置**: 系统临时目录 `/file-share/`
- **最大文件大小**: 2GB

## ✨ 功能特性

✅ 简洁现代的 UI 设计
✅ 支持拖拽上传
✅ 实时传输进度显示
✅ WebSocket 设备发现
✅ P2P 直连传输（使用 Netty）
✅ 响应式设计（适配手机和电脑）
✅ 支持所有文件类型
✅ 批量上传

## 🛠️ 技术栈

- Spring Boot 2.7.0
- Netty 4.1.79.Final (P2P 传输)
- WebSocket (设备发现)
- Vanilla JS (前端，无框架依赖)

## 📦 打包部署

```bash
# 编译打包
mvn clean package -DskipTests

# 生成的可执行 JAR 位置
target/DailyPractice.jar

# 后台运行（Linux/Mac）
nohup java -jar DailyPractice.jar &

# 后台运行（Windows）
start javaw -jar DailyPractice.jar
```

## 🎨 界面预览

- 主色调：渐变紫色 (#667eea → #764ba2)
- 简洁卡片式设计
- 移动端友好布局

## ⚙️ 自定义配置

编辑 `src/main/resources/application.properties`:

```properties
# 修改 Web 端口
server.port=8081

# 修改 P2P 端口
p2p.server.port=8082

# 修改最大文件大小
spring.servlet.multipart.max-file-size=4096MB
spring.servlet.multipart.max-request-size=4096MB
```

## 🔐 安全提示

当前版本为内网使用设计，如需外网访问请添加认证机制。

## 📝 License

MIT License
