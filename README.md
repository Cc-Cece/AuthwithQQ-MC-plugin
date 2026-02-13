# QQBindingGuestMode - QQ 绑定游客模式插件 (Paper 1.16+)

这是一个用于 Paper/Spigot 服务器的插件，旨在强制未完成 QQ 绑定的玩家进入游客模式（Adventure Mode），直到他们完成外部认证。

## 🚀 功能特性

*   **强制游客模式**: 玩家首次加入服务器时，如果未绑定，将被强制设置为 `Adventure` 模式。
*   **操作限制**: 限制未绑定玩家进行破坏、放置、拾取、丢弃物品以及与方块/实体交互等操作。
*   **异步通信**: 所有与外部绑定 API 的网络通信都在异步线程中执行，确保服务器主线程不被阻塞。
*   **控制台通知**: 提供控制台命令，用于在外部绑定成功后通知服务器更新玩家状态。

## ⚙️ 配置 (`config.yml`)

插件的配置文件位于 `plugins/QQBindingGuestMode/config.yml`。

| 配置项 | 默认值 | 描述 |
| :--- | :--- | :--- |
| `backend-api-url` | `"http://your.backend.com/api/getBindingStatus?mcid="` | 外部绑定 API 的 URL。插件将玩家 MCID 附加到此 URL 后进行查询。 |
| `message-unbound-prompt` | `"§c欢迎！请加入 QQ 群并发送 /绑定 [数字] 完成认证，否则无法进行操作。"` | 玩家加入时，如果未绑定，收到的提示消息。 |
| `message-unbound-restriction` | `"§c请先完成 QQ 绑定！"` | 玩家尝试进行受限操作时收到的提示消息。 |
| `message-bind-success` | `"§a账号绑定成功！您现在可以正常游戏了。"` | 通过 `/qqbindsuccess` 命令更新状态后，玩家收到的成功消息。 |

## 🕹️ 命令

| 命令 | 权限 | 描述 |
| :--- | :--- | :--- |
| `/qqbindsuccess <MCID>` | `qqbinding.admin` (默认 OP) | **仅限控制台执行。** 用于在外部绑定成功后，强制将指定在线玩家的模式设置为 `Survival`。 |

## 🛠️ 依赖

本插件需要一个外部 RESTful API 服务来查询绑定状态，例如本项目的配套后端服务。请确保 `backend-api-url` 配置正确指向您的后端服务。

### API 接口要求

插件调用以下 GET 接口：

`GET {backend-api-url}<MCID>`

**预期响应示例 (JSON):**

```json
{
  "isBound": true,  // 或 false
  "bindingCode": null,
  "expiresAt": null
}

## 🧪 测试与模拟

### 1. 模拟绑定状态查询

为了测试插件的 `PlayerJoinEvent` 逻辑，您需要确保后端 API 返回正确的 JSON 响应。

**模拟未绑定状态 (玩家进入时应被设置为 Adventure 模式):**

```json
{
  "isBound": false,
  "bindingCode": "123456",
  "expiresAt": "2025-11-14 21:35:00"
}
```

**模拟已绑定状态 (玩家进入时应被设置为 Survival 模式):**

```json
{
  "isBound": true,
  "bindingCode": null,
  "expiresAt": null
}
```

### 2. 模拟绑定成功通知

当玩家在外部系统（如 QQ 机器人）完成绑定后，后端服务应向服务器发送通知，触发 `/qqbindsuccess` 命令。

**在服务器控制台手动测试命令：**

假设玩家 `TestPlayer` 在线，执行以下命令：

```bash
qqbindsuccess TestPlayer
```

执行后，`TestPlayer` 的游戏模式应立即切换为 `Survival`，并收到配置的成功消息。
