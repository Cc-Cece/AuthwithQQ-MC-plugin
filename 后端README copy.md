# AuthwithQQ - MC 服务器与 QQ 机器人账号绑定认证系统

本项目是一个基于 Spring Boot 的 RESTful API 服务，用于实现 Minecraft (MC) 服务器玩家与 QQ 账号之间的绑定认证。它使用 H2 嵌入式数据库以文件模式存储绑定数据，确保数据持久性。

## 核心技术栈

*   **框架:** Java 17, Spring Boot 3.2.0
*   **数据库:** H2 嵌入式数据库 (FILE 模式)
*   **数据访问:** Spring Data JPA
*   **异步通知:** RestTemplate, `@EnableAsync`

## 运行环境要求

*   Java 17 或更高版本
*   Maven

## 配置说明

项目运行时，Spring Boot 会查找外部配置文件 `config/application.yml`。如果该文件不存在，应用将在启动时自动创建包含默认配置的 `config/application.yml`。

**默认配置项（可在 `config/application.yml` 中覆盖）：**

| 配置项 | 描述 | 默认值 |
| :--- | :--- | :--- |
| `server.port` | 应用运行端口 | `8080` |
| `spring.datasource.url` | H2 数据库文件路径 | `jdbc:h2:file:./data/mc_qq_binding` |
| `spring.h2.console.enabled` | 是否启用 H2 Console | `true` |
| `spring.h2.console.path` | H2 Console 访问路径 | `/h2-console` |
| `server.notification.url` | MC 服务器 WebHook/API 地址，用于发送绑定成功通知。该接口应接收一个包含 `command` 字段的 JSON 请求体。 | `http://localhost:8081/api/executeCommand` |
| `auth.binding.expiry-minutes` | 绑定码有效期 (分钟) | `5` |
| `auth.binding.code-length` | 绑定码长度 (位数) | `6` |

### 启动项目

```bash
# 编译打包
mvn clean package

# 运行 JAR 包
# Spring Boot 默认会查找 ./config/ 目录下的配置文件 (如果不存在，应用会自动创建 config/application.yml)
java -jar target/AuthwithQQ-0.0.1-SNAPSHOT.jar
```

## 数据库实体

### 1. PlayerBinding (永久绑定记录)

| 字段 | 类型 | 描述 | 约束 |
| :--- | :--- | :--- | :--- |
| `mcName` | String | MC 玩家名称 | 主键 |
| `qqId` | String | QQ ID | |
| `bindDate` | LocalDateTime | 绑定时间 | |

### 2. BindingCode (临时绑定码)

| 字段 | 类型 | 描述 | 约束 |
| :--- | :--- | :--- | :--- |
| `id` | Long | 唯一 ID | 主键, 自增 |
| `code` | String | 6 位数字绑定码 (长度由 `auth.binding.code-length` 控制) | |
| `mcName` | String | MC 玩家名称 | |
| `expiresAt` | LocalDateTime | 过期时间 (由 `auth.binding.expiry-minutes` 控制) | |

## RESTful API 接口

### 1. 查询状态/生成码

用于 MC 插件查询玩家绑定状态，如果未绑定则生成临时绑定码。

*   **路径:** `GET /api/getBindingStatus`
*   **参数:**
    *   `mcid` (Query Parameter): MC 玩家名称
*   **响应 (JSON):**

**模拟请求 (MC 插件):**
```bash
# 假设服务运行在 8080 端口
curl -X GET "http://localhost:8080/api/getBindingStatus?mcid=TestPlayer"
```

| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `bound` | boolean | 玩家是否已永久绑定 |
| `bindingCode` | String | 如果未绑定，返回绑定码 |
| `expiresAt` | String | 绑定码过期时间 (格式: YYYY-MM-DD HH:mm:ss) |

**示例 (未绑定):**
```json
{
  "bindingCode": "422505",
  "expiresAt": "2025-11-15 00:27:29",
  "bound": false
}
```

**示例 (已绑定):**
```json
{
  "bindingCode": null,
  "expiresAt": null,
  "bound": true
}
```

### 2. 验证并绑定

用于 QQ 机器人验证绑定码，并将 MC 玩家与 QQ ID 永久绑定。

*   **路径:** `POST /api/verifyAndBind`
*   **请求体 (JSON):**

**模拟请求 (QQ 机器人):**
```bash
curl -X POST "http://localhost:8080/api/verifyAndBind" \
     -H "Content-Type: application/json" \
     -d '{"code": "123456", "qqId": "123456789"}'
```

```json
{
  "code": "123456",
  "qqId": "123456789"
}
```
*   **响应:**
    *   **成功 (200 OK):** `{"success": true}`
    *   **失败 (400 Bad Request):** `{"success": false}` (原因可能是 code 无效/过期，或 QQ ID 已被绑定)

## 服务器通知机制

绑定成功后，`ServerNotificationService` 会异步向 `server.notification.url` 发送一个 HTTP POST 请求，请求体包含要执行的命令，例如：

```json
{
  "command": "/qqbindsuccess <MCID>"
}