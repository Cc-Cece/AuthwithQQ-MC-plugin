package com.cccece.authwithqq.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * AuthWithQQ Velocity 代理端插件.
 * 在玩家登录时异步验证其 QQ 绑定状态；未绑定的玩家将被踢出并告知绑定码.
 *
 * <p>注意：{@code @Plugin} 注解中的 {@code version} 为编译时占位符；
 * Velocity 运行时版本以 {@code velocity-plugin.json} 中的值（由构建注入）为准.
 */
@Plugin(
    id = "authwithqq-velocity",
    name = "AuthwithQQ-Velocity",
    version = "1.0.0",
    description = "QQ binding authentication plugin for Velocity proxy",
    authors = {"Mizuki"}
)
public class AuthWithQqVelocityPlugin {

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;

  private VelocityBindingApi bindingApi;
  private String messageUnbound;
  private String messageUnboundNoCode;
  private boolean debugMode;

  /**
   * 构造函数 (由 Velocity 的 Guice 注入器调用).
   *
   * @param server        代理服务器实例.
   * @param logger        日志记录器.
   * @param dataDirectory 插件数据目录.
   */
  @Inject
  public AuthWithQqVelocityPlugin(ProxyServer server, Logger logger,
                                   @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  /**
   * 代理初始化时加载配置.
   *
   * @param event 代理初始化事件.
   */
  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    loadConfig();
    logger.info("AuthWithQQ Velocity 插件已启动 (代理端模式)！");
    logger.info("所有玩家登录时将进行 QQ 绑定验证。");
  }

  /**
   * 玩家登录事件处理 — 异步检查 QQ 绑定状态.
   * 未绑定的玩家将被拒绝登录并收到绑定码提示.
   *
   * @param event 登录事件.
   * @return 异步任务 (在独立线程中执行 HTTP 检查).
   */
  @Subscribe
  public EventTask onLogin(LoginEvent event) {
    return EventTask.async(() -> {
      String playerName = event.getPlayer().getUsername();
      try {
        VelocityBindingApi.BindingStatus status = bindingApi.getBindingStatus(playerName);
        if (!status.isBound()) {
          String kickMessage = buildKickMessage(status.getBindingCode());
          event.setResult(ComponentResult.denied(
              LegacyComponentSerializer.legacySection().deserialize(kickMessage)
          ));
          if (debugMode) {
            logger.info("玩家 " + playerName + " 未绑定 QQ，已拒绝登录。");
          }
        } else if (debugMode) {
          logger.info("玩家 " + playerName + " 已绑定 QQ，允许登录。");
        }
      } catch (Exception e) {
        // 出错时允许玩家进入 (fail-open)，避免 API 故障影响所有玩家
        logger.severe("检查玩家 " + playerName + " 绑定状态时出错: " + e.getMessage());
      }
    });
  }

  /**
   * 根据绑定码构建踢出消息.
   *
   * @param bindingCode 绑定码，若为 null 则使用无码模板.
   * @return 格式化后的踢出消息.
   */
  private String buildKickMessage(String bindingCode) {
    if (bindingCode != null && !bindingCode.isEmpty()) {
      return messageUnbound.replace("{bindingCode}", bindingCode);
    }
    return messageUnboundNoCode;
  }

  /**
   * 加载插件配置文件.
   */
  private void loadConfig() {
    try {
      Files.createDirectories(dataDirectory);
    } catch (IOException e) {
      logger.severe("无法创建数据目录: " + e.getMessage());
    }

    Path configFile = dataDirectory.resolve("config.yml");

    // 若配置文件不存在，从 JAR 内部复制默认配置
    if (!Files.exists(configFile)) {
      try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
        if (in != null) {
          Files.copy(in, configFile);
        }
      } catch (IOException e) {
        logger.severe("无法复制默认配置文件: " + e.getMessage());
      }
    }

    // 读取配置
    Map<String, String> config = new LinkedHashMap<>();
    try {
      config = readSimpleYaml(configFile);
    } catch (IOException e) {
      logger.severe("无法读取配置文件，使用内置默认值: " + e.getMessage());
    }

    String apiUrl = config.getOrDefault("backend-api-url", "http://your.api.com/");
    this.debugMode = Boolean.parseBoolean(config.getOrDefault("debug-mode", "false"));
    this.messageUnbound = config.getOrDefault("message-unbound",
        "§c您尚未绑定 QQ 账号！绑定码：{bindingCode}");
    this.messageUnboundNoCode = config.getOrDefault("message-unbound-no-code",
        "§c您尚未绑定 QQ 账号！");

    this.bindingApi = new VelocityBindingApi(apiUrl, logger, debugMode);

    // 自检：提示默认 API URL
    if (apiUrl.contains("your.api.com")) {
      logger.warning("==================================================");
      logger.warning("!!! 警告: backend-api-url 仍是默认值，请修改配置文件！");
      logger.warning("==================================================");
    }
  }

  /**
   * 简单的 YAML 平坦键值读取器（支持双引号字符串与 \n 转义）.
   *
   * @param filePath 配置文件路径.
   * @return 键值对映射.
   * @throws IOException 读取失败时抛出.
   */
  static Map<String, String> readSimpleYaml(Path filePath) throws IOException {
    Map<String, String> result = new LinkedHashMap<>();
    try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || trimmed.isEmpty()) {
          continue;
        }
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
          String key = trimmed.substring(0, colonIndex).trim();
          String value = trimmed.substring(colonIndex + 1).trim();
          // 处理双引号字符串
          if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
            // 转换常见转义序列
            value = value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
          }
          result.put(key, value);
        }
      }
    }
    return result;
  }

  /**
   * 获取代理服务器实例（供测试使用）.
   *
   * @return 代理服务器.
   */
  public ProxyServer getServer() {
    return server;
  }
}
