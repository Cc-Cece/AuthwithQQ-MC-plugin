package com.crimsonwarpedcraft.qqbindingguestmode;

import io.papermc.lib.PaperLib;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * QQ 绑定游客模式插件的主类。
 */
public class QQBindingGuestModePlugin extends JavaPlugin {

  private BindingAPI bindingAPI;
  private String unboundPromptMessage;
  private String unboundRestrictionMessage;
  private String bindSuccessMessage;
  private boolean isDebugMode;
  private Set<String> playersToPoll; // 存储需要轮询的玩家名称 (MCID)
  private int pollingTaskId = -1; // 存储定时任务 ID

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);
    saveDefaultConfig();
    reloadConfigData();
    this.playersToPoll = Collections.synchronizedSet(new HashSet<>());

    // 插件自检
    selfCheck();

    // 启动定时轮询任务
    startPollingTask();

    // 注册事件监听器
    getServer().getPluginManager().registerEvents(new BindingListener(this), this);

    // 注册命令
    // 旧的 /qqbindsuccess 命令已弃用，因为绑定状态现在通过轮询检查。
  }

  @Override
  public void onDisable() {
    // 停止定时任务
    if (pollingTaskId != -1) {
      Bukkit.getScheduler().cancelTask(pollingTaskId);
    }
  }

  /**
   * 插件启动自检。
   */
  private void selfCheck() {
    String apiUrl = getConfig().getString("backend-api-url");
    boolean defaultUrl = apiUrl.contains("your.backend.com");

    if (defaultUrl) {
      getLogger().severe("==================================================");
      getLogger().severe("!!! 警告: backend-api-url 仍是默认值!");
      getLogger().severe("!!! 请在 config.yml 中配置正确的后端 API 地址。");
      getLogger().severe("==================================================");
    }

    if (isDebugMode) {
      getLogger().info("==================================================");
      getLogger().info("Debug Mode is ENABLED.");
      getLogger().info("Backend API URL: " + apiUrl);
      getLogger().info("Unbound Prompt: " + unboundPromptMessage);
      getLogger().info("==================================================");
    }

    // 异步执行 API 连接测试
    if (!defaultUrl) {
      getLogger().info("Starting API connection self-check...");
      CompletableFuture.supplyAsync(() -> bindingAPI.getApiStatusCode("__TEST__"))
          .thenAccept(statusCode -> {
            if (statusCode == 200) {
              getLogger().info("API connection successful! Status Code: 200 OK.");
            } else if (statusCode == -1) {
              getLogger().severe("API connection FAILED! Check network connectivity or API URL.");
            } else {
              getLogger().warning("API connection successful, but returned non-200 status code: " + statusCode);
              getLogger().warning("This might indicate a configuration issue or an unhandled API error.");
            }
          });
    }
  }

  /**
   * 重新加载配置数据。
   */
  public void reloadConfigData() {
    reloadConfig();
    this.isDebugMode = getConfig().getBoolean("debug-mode", false);
    
    // 假设配置中只存储基础 URL，例如 http://your.backend.com/
    String apiUrl = getConfig().getString("backend-api-url", "http://your.backend.com/");
    this.bindingAPI = new BindingAPI(this, apiUrl);

    this.unboundPromptMessage = getConfig().getString("message-unbound-prompt", "§c欢迎！请加入 QQ 群并发送 /绑定 {CODE} 完成认证，否则无法进行操作。");
    this.unboundRestrictionMessage = getConfig().getString("message-unbound-restriction", "§c请先完成 QQ 绑定！绑定码: {CODE}");
    this.bindSuccessMessage = getConfig().getString("message-bind-success", "§a账号绑定成功！您现在可以正常游戏了。");
  }

  /**
   * 获取绑定 API 实例。
   * @return BindingAPI 实例。
   */
  public BindingAPI getBindingAPI() {
    return bindingAPI;
  }

  public String getUnboundPromptMessage() {
    return unboundPromptMessage;
  }

  public String getUnboundRestrictionMessage() {
    return unboundRestrictionMessage;
  }

  public String getBindSuccessMessage() {
    return bindSuccessMessage;
  }

  public boolean isDebugMode() {
    return isDebugMode;
  }

  /**
   * 获取需要轮询的玩家集合。
   */
  public Set<String> getPlayersToPoll() {
    return playersToPoll;
  }

  /**
   * 启动定时轮询任务。
   */
  private void startPollingTask() {
    long delay = 20L * 5; // 5 秒延迟
    long period = 20L * 10; // 每 10 秒轮询一次

    // 确保在 onDisable 时可以取消任务
    pollingTaskId = Bukkit.getScheduler().runTaskTimer(this, this::pollBindingStatus, delay, period).getTaskId();
    getLogger().info("Binding status polling task started (Interval: 10s).");
  }

  /**
   * 执行轮询逻辑。
   */
  private void pollBindingStatus() {
    // 复制集合以避免在迭代时修改
    Set<String> currentPlayers = new HashSet<>(playersToPoll);

    if (isDebugMode) {
      getLogger().info("Polling binding status for " + currentPlayers.size() + " players.");
    }

    for (String playerName : currentPlayers) {
      Player player = Bukkit.getPlayerExact(playerName);

      // 玩家可能已离线或已绑定并被移除
      if (player == null || !player.isOnline()) {
        playersToPoll.remove(playerName);
        continue;
      }

      // 异步检查绑定状态
      bindingAPI.getBindingStatusAsync(playerName)
          .thenAccept(status -> {
            if (status.isBound()) {
              // 绑定成功，在主线程中处理
              Bukkit.getScheduler().runTask(this, () -> handleBindingSuccess(player));
            }
          });
    }
  }

  /**
   * 处理玩家绑定成功后的逻辑。
   * @param player 已绑定成功的玩家。
   */
  public void handleBindingSuccess(Player player) {
    String playerName = player.getName();
    
    // 确保玩家仍在需要轮询的列表中，防止重复处理
    if (playersToPoll.contains(playerName)) {
      playersToPoll.remove(playerName);

      // 1. 将玩家的 GameMode 设为 Survival 模式
      player.setGameMode(org.bukkit.GameMode.SURVIVAL);

      // 2. 向玩家发送成功的提示消息
      String successMessage = getBindSuccessMessage();
      BindingAPI.sendPlayerMessage(this, player, successMessage);

      getLogger().info("Player " + playerName + " successfully bound via polling.");
    }
  }

  /**
   * 静态方法获取插件实例，方便其他类调用。
   *
   * @return 插件实例。
   */
  public static QQBindingGuestModePlugin getInstance() {
    return JavaPlugin.getPlugin(QQBindingGuestModePlugin.class);
  }
}