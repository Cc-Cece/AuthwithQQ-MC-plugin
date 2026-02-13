package com.cccece.authwithqq;

import io.papermc.lib.PaperLib;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * QQ 绑定游客模式插件的主类.
 * 已集成白名单豁免功能，支持通过指令 /qqskip 或 /qqwhitelist 永久豁免玩家.
 */
public class AuthWithQqPlugin extends JavaPlugin {

  private BindingApi bindingApi;
  private String unboundPromptMessage;
  private String unboundRestrictionMessage;
  private String bindSuccessMessage;
  private boolean isDebugMode;
  private Set<String> playersToPoll; // 存储需要轮询的玩家名称 (MCID)
  private int pollingTaskId = -1; // 存储定时任务 ID
  private BindingListener bindingListener; // 监听器引用
  private WhitelistManager whitelistManager; // 白名单管理器

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);
    saveDefaultConfig();
    
    // 初始化白名单管理器 (加载 whitelist.yml)
    this.whitelistManager = new WhitelistManager(this);
    
    reloadConfigData();
    this.playersToPoll = Collections.synchronizedSet(new HashSet<>());

    // 插件自检
    selfCheck();

    // 启动定时轮询任务
    startPollingTask();

    // 注册事件监听器
    this.bindingListener = new BindingListener(this);
    getServer().getPluginManager().registerEvents(bindingListener, this);

    // 注册豁免指令
    if (getCommand("qqskip") != null) {
        getCommand("qqskip").setExecutor(new SkipCommand());
    }
  }

  @Override
  public void onDisable() {
    // 停止定时任务
    if (pollingTaskId != -1) {
      Bukkit.getScheduler().cancelTask(pollingTaskId);
    }
  }

  /**
   * 插件启动自检.
   */
  private void selfCheck() {
    String apiUrl = getConfig().getString("backend-api-url");
    boolean defaultUrl = apiUrl != null && apiUrl.contains("your.backend.com");

    if (defaultUrl) {
      getLogger().severe("==================================================");
      getLogger().severe("!!! 警告: backend-api-url 仍是默认值!");
      getLogger().severe("==================================================");
    }

    // 异步执行 API 连接测试
    if (!defaultUrl && bindingApi != null) {
      CompletableFuture.supplyAsync(() -> bindingApi.getApiStatusCode("__TEST__"))
          .thenAccept(statusCode -> {
            if (statusCode == 200) {
              getLogger().info("API connection successful! Status Code: 200 OK.");
            } else if (statusCode == -1) {
              getLogger().severe("API connection FAILED! Check network connectivity.");
            }
          });
    }
  }

  /**
   * 重新加载配置数据.
   */
  public void reloadConfigData() {
    reloadConfig();
    this.isDebugMode = getConfig().getBoolean("debug-mode", false);
    
    String apiUrl = getConfig().getString("backend-api-url", "http://your.backend.com/");
    this.bindingApi = new BindingApi(this, apiUrl);

    this.unboundPromptMessage = getConfig().getString("message-unbound-prompt", "§c欢迎！...");
    this.unboundRestrictionMessage = getConfig().getString("message-unbound-restriction", "§c请先完成 QQ 绑定！");
    this.bindSuccessMessage = getConfig().getString("message-bind-success", "§a账号绑定成功！");
    
    if (whitelistManager != null) {
        whitelistManager.reload();
    }
  }

  /**
   * 豁免指令处理类.
   * 支持 /qqskip add|remove <Name|UUID>
   */
  private class SkipCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
      if (args.length < 2) {
        sender.sendMessage("§c用法: /" + label + " <add|remove> <玩家名|UUID>");
        return true;
      }

      String action = args[0].toLowerCase();
      String target = args[1];

      if (action.equals("add")) {
        try {
          UUID uuid = UUID.fromString(target);
          whitelistManager.addUuid(uuid);
          sender.sendMessage("§a已将 UUID " + target + " 添加到豁免名单。");
        } catch (IllegalArgumentException e) {
          whitelistManager.addName(target);
          sender.sendMessage("§a已将名称 " + target + " 添加到豁免名单。");
        }
        
        // 如果玩家在线，立即解除限制
        Player onlinePlayer = Bukkit.getPlayerExact(target);
        if (onlinePlayer != null) {
            handleBindingSuccess(onlinePlayer);
        }
      } else if (action.equals("remove")) {
        try {
          UUID uuid = UUID.fromString(target);
          whitelistManager.removeUuid(uuid);
          sender.sendMessage("§e已移除 UUID " + target + " 的豁免权限。");
        } catch (IllegalArgumentException e) {
          whitelistManager.removeName(target);
          sender.sendMessage("§e已移除名称 " + target + " 的豁免权限。");
        }
      }
      return true;
    }
  }

  public BindingApi getBindingApi() { return bindingApi; }
  public BindingListener getBindingListener() { return bindingListener; }
  public WhitelistManager getWhitelistManager() { return whitelistManager; }
  public String getUnboundPromptMessage() { return unboundPromptMessage; }
  public String getUnboundRestrictionMessage() { return unboundRestrictionMessage; }
  public String getBindSuccessMessage() { return bindSuccessMessage; }
  public boolean isDebugMode() { return isDebugMode; }
  public Set<String> getPlayersToPoll() { return playersToPoll; }

  private void startPollingTask() {
    long delay = 20L * 5;
    long period = 20L * 10;
    pollingTaskId = Bukkit.getScheduler().runTaskTimer(this, this::pollBindingStatus, delay, period).getTaskId();
  }

  private void pollBindingStatus() {
    Set<String> currentPlayers = new HashSet<>(playersToPoll);
    for (String playerName : currentPlayers) {
      Player player = Bukkit.getPlayerExact(playerName);
      if (player == null || !player.isOnline()) {
        playersToPoll.remove(playerName);
        continue;
      }
      bindingApi.getBindingStatusAsync(playerName).thenAccept(status -> {
        if (status.isBound()) {
          Bukkit.getScheduler().runTask(this, () -> handleBindingSuccess(player));
        }
      });
    }
  }

  public void handleBindingSuccess(Player player) {
    String playerName = player.getName();
    playersToPoll.remove(playerName);
    if (bindingListener != null) {
      bindingListener.markVerified(player.getUniqueId());
    }
    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
    BindingApi.sendPlayerMessage(this, player, getBindSuccessMessage());
  }

  public static AuthWithQqPlugin getInstance() {
    return JavaPlugin.getPlugin(AuthWithQqPlugin.class);
  }
}