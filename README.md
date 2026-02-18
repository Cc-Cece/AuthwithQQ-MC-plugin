# AuthWithQQ - QQ 绑定游客模式插件

AuthWithQQ 是一款为 Paper/Spigot 服务器设计的安全增强插件。它要求玩家必须完成外部 QQ 绑定才能获得完整的游戏权限，否则将被限制在冒险模式中。

---

## 🛠️ 核心功能

*   **验证状态管理**: 玩家加入时自动查询绑定状态。未绑定玩家强制设为 `冒险模式`。
*   **高度可配置的行为拦截**:
    *   **移动限制**: 可配置是否允许原地转头或自由移动游览。
    *   **世界/传送限制**: 可配置是否禁止跨世界切换或离开初始世界。
    *   **世界交互**: 可配置是否禁止破坏方块、放置方块、与方块/实体交互。
    *   **经济/物品**: 禁止丢弃物品、捡起物品、对实体造成伤害。
    *   **社交/指令**: 禁止发送聊天消息；拦截除 `/login`, `/register`, `/l` 以外的所有指令。
*   **白名单(豁免)系统**: 支持通过 `/qqskip` 将特定玩家或 UUID 加入豁免名单，跳过所有验证逻辑。
*   **后台自动轮询**: 每 10 秒自动轮询一次未验证玩家的状态，绑定成功后即时解除限制。

---

## ⚙️ 配置文件 (`config.yml`)

```yaml
# 外部 API 的基础地址
backend-api-url: "http://your.api.com/"

# 调试模式
debug-mode: false

# --- 限制行为配置 ---

# 是否禁止未绑定玩家移动（设为 false 则玩家可以自由走动游览）
restrict-movement: true

# 是否禁止未绑定玩家切换世界（防止通过传送门等离开当前世界）
restrict-world-change: true

# 是否禁止未绑定玩家交互（右键方块、实体、箱子等）
restrict-interaction: true

# 是否禁止未绑定玩家破坏方块
restrict-block-break: true

# 是否禁止未绑定玩家放置方块
restrict-block-place: true

# --- 消息配置 ---
message-unbound-prompt: "§c欢迎！请先完成 QQ 绑定以获得权限。"
message-unbound-restriction: "§c请先完成 QQ 绑定！"
message-bind-success: "§a账号绑定成功！"
```

---

## 🕹️ 指令与权限

| 指令 | 别名 | 描述 | 权限 |
| :--- | :--- | :--- | :--- |
| `/qqskip add <玩家名\|UUID>` | `/qqwhitelist` | 豁免指定玩家的绑定要求 | `qqbinding.admin` |
| `/qqskip remove <玩家名\|UUID>` | `/qqwhitelist` | 移除玩家的豁免权限 | `qqbinding.admin` |

---

## 🌐 API 规范与示例

插件验证玩家时发起 **GET** 请求：
`{backend-api-url}/api/getBindingStatus?mcid={playerName}`

**预期响应 (JSON)：**
```json
{
  "bound": true,
  "bindingCode": "12345"
}
```

---

## 🚀 开发者说明
本项目使用 Gradle 编译，支持 Java 21。
```bash
./gradlew build
```
