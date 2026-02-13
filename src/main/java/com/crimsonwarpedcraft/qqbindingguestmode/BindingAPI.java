package com.crimsonwarpedcraft.qqbindingguestmode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 处理与外部 QQ 绑定 API 的通信。
 */
public class BindingAPI {

  private final QQBindingGuestModePlugin plugin;
  private final String apiUrl;

  /**
   * 构造函数。
   *
   * @param plugin 插件实例。
   * @param apiUrl 绑定 API URL。
   */
  public BindingAPI(QQBindingGuestModePlugin plugin, String apiUrl) {
    this.plugin = plugin;
    this.apiUrl = apiUrl;
  }

  /**
   * 同步方法：获取 API 响应码。
   *
   * @param playerName 用于构建 URL 的玩家名称 (MCID)。
   * @return HTTP 响应码 (例如 200, 404)，如果发生连接错误则返回 -1。
   */
  public int getApiStatusCode(String playerName) {
    String urlString = buildBindingStatusUrl(playerName);
    if (plugin.isDebugMode()) {
      plugin.getLogger().log(Level.INFO, "Testing API URL: " + urlString);
    }
    try {
      URL url = new URL(urlString);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      // 尝试连接并获取响应码
      connection.connect();
      int responseCode = connection.getResponseCode();
      connection.disconnect();
      return responseCode;
    } catch (Exception e) {
      if (plugin.isDebugMode()) {
        plugin.getLogger().log(Level.WARNING, "API connection test failed for URL: " + urlString, e);
      }
      return -1; // 表示连接失败
    }
  }

  /**
   * 异步检查玩家是否已绑定。
   *
   * @param playerName 玩家的名称 (MCID)。
   * @return CompletableFuture<BindingStatus> 包含绑定状态和绑定码。
   */
  public CompletableFuture<BindingStatus> getBindingStatusAsync(String playerName) {
    CompletableFuture<BindingStatus> future = new CompletableFuture<>();

    // 必须在异步线程中执行网络请求
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      String urlString = buildBindingStatusUrl(playerName);
      if (plugin.isDebugMode()) {
        plugin.getLogger().log(Level.INFO, "Checking binding status for " + playerName + ". URL: " + urlString);
      }
      try {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
          BufferedReader in = new BufferedReader(new InputStreamReader(
              connection.getInputStream()));
          String inputLine;
          StringBuilder response = new StringBuilder();

          while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
          }
          in.close();

          // 解析 JSON 响应，查找 "bound" 和 "bindingCode"
          String responseBody = response.toString();
          boolean isBound = false;
          String bindingCode = null;

          // 1. 解析 "bound" 字段
          Pattern boundPattern = Pattern.compile("\"bound\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
          Matcher boundMatcher = boundPattern.matcher(responseBody);

          if (boundMatcher.find()) {
            isBound = boundMatcher.group(1).equalsIgnoreCase("true");
          } else {
            plugin.getLogger().log(Level.WARNING,
                "Failed to parse 'bound' field for " + playerName + ". Response: " + responseBody);
          }

          // 2. 解析 "bindingCode" 字段
          // 匹配 "bindingCode" 字段后紧跟的字符串值 (可能为 null 或数字字符串)
          Pattern codePattern = Pattern.compile("\"bindingCode\"\\s*:\\s*\"?([^\",]*)\"?", Pattern.CASE_INSENSITIVE);
          Matcher codeMatcher = codePattern.matcher(responseBody);

          if (codeMatcher.find()) {
            bindingCode = codeMatcher.group(1);
            if (bindingCode.equalsIgnoreCase("null")) {
              bindingCode = null;
            }
          }

          if (plugin.isDebugMode()) {
            plugin.getLogger().log(Level.INFO, "API response for " + playerName + ": " + responseBody);
          }

          future.complete(new BindingStatus(isBound, bindingCode));
        } else {
          plugin.getLogger().log(Level.WARNING,
              "API request failed for player " + playerName + ". Response code: " + responseCode);
          if (plugin.isDebugMode()) {
            plugin.getLogger().log(Level.INFO, "Failed API URL: " + urlString);
          }
          // 默认未绑定或发生错误时，返回未绑定状态和 null 绑定码
          future.complete(new BindingStatus(false, null));
        }
      } catch (Exception e) {
        plugin.getLogger().log(Level.SEVERE,
            "Error checking binding status for player " + playerName, e);
        if (plugin.isDebugMode()) {
          plugin.getLogger().log(Level.INFO, "Failed API URL: " + urlString);
        }
        future.complete(new BindingStatus(false, null));
      }
    });

    return future;
  }

  /**
   * 构建查询绑定状态的完整 URL。
   * @param playerName 玩家名称 (MCID)。
   * @return 完整的 API URL 字符串。
   */
  private String buildBindingStatusUrl(String playerName) {
    // 确保 apiUrl 以斜杠结尾
    String baseUrl = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
    // 拼接新的 API 路径和查询参数
    return baseUrl + "api/getBindingStatus?mcid=" + playerName;
  }

  /**
   * 辅助方法：在主线程中向玩家发送消息。
   */
  public static void sendPlayerMessage(JavaPlugin plugin, Player player, String message) {
    if (player != null && player.isOnline()) {
      Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }
  }
  /**
   * 封装 API 响应的绑定状态和绑定码。
   */
  public static class BindingStatus {
    private final boolean isBound;
    private final String bindingCode;

    public BindingStatus(boolean isBound, String bindingCode) {
      this.isBound = isBound;
      this.bindingCode = bindingCode;
    }

    public boolean isBound() {
      return isBound;
    }

    public String getBindingCode() {
      return bindingCode;
    }
  }
}