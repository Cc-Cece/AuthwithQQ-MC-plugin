package com.cccece.authwithqq;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 处理与外部 QQ 绑定 API 的通信.
 */
public class BindingApi {

  private static final Pattern BOUND_PATTERN = Pattern.compile(
      "\"bound\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CODE_PATTERN = Pattern.compile(
      "\"bindingCode\"\\s*:\\s*\"?([^\",]*)\"?", Pattern.CASE_INSENSITIVE);

  private final AuthWithQqPlugin plugin;
  private final String apiUrl;

  /**
   * 构造函数.
   *
   * @param plugin 插件实例.
   * @param apiUrl 绑定 API URL.
   */
  public BindingApi(AuthWithQqPlugin plugin, String apiUrl) {
    this.plugin = plugin;
    this.apiUrl = apiUrl;
  }

  /**
   * 同步方法：获取 API 响应码.
   *
   * @param playerName 用于构建 URL 的玩家名称 (MCID).
   * @return HTTP 响应码 (例如 200, 404)，如果发生连接错误则返回 -1.
   */
  public int getApiStatusCode(String playerName) {
    String urlString = buildBindingStatusUrl(playerName);
    if (plugin.isDebugMode()) {
      plugin.getLogger().log(Level.INFO, "Testing API URL: {0}", urlString);
    }
    HttpURLConnection connection = null;
    try {
      URI uri = URI.create(urlString);
      URL url = uri.toURL();
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      // 尝试连接并获取响应码
      connection.connect();
      return connection.getResponseCode();
    } catch (Exception e) {
      if (plugin.isDebugMode()) {
        plugin.getLogger().log(Level.WARNING,
            "API connection test failed for URL: " + urlString, e);
      }
      return -1; // 表示连接失败
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * 异步方法：获取 API 响应码.
   *
   * @param playerName 玩家名称
   * @return CompletableFuture 包含响应码
   */
  public CompletableFuture<Integer> getApiStatusCodeAsync(String playerName) {
    CompletableFuture<Integer> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      future.complete(getApiStatusCode(playerName));
    });
    return future;
  }

  /**
   * 异步检查玩家是否已绑定.
   *
   * @param playerName 玩家的名称 (MCID).
   * @return CompletableFuture 包含绑定状态和绑定码.
   */
  public CompletableFuture<BindingStatus> getBindingStatusAsync(String playerName) {
    CompletableFuture<BindingStatus> future = new CompletableFuture<>();

    // 必须在异步线程中执行网络请求
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      String urlString = buildBindingStatusUrl(playerName);
      if (plugin.isDebugMode()) {
        plugin.getLogger().log(Level.INFO,
            "Checking binding status for {0}. URL: {1}", new Object[]{playerName, urlString});
      }
      HttpURLConnection connection = null;
      try {
        URI uri = URI.create(urlString);
        URL url = uri.toURL();
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
          StringBuilder response = new StringBuilder();
          try (BufferedReader in = new BufferedReader(new InputStreamReader(
              connection.getInputStream(), StandardCharsets.UTF_8))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
              response.append(inputLine);
            }
          }

          // 解析 JSON 响应，查找 "bound" 和 "bindingCode"
          String responseBody = response.toString();
          boolean isBound = false;
          String bindingCode = null;

          // 1. 解析 "bound" 字段
          Matcher boundMatcher = BOUND_PATTERN.matcher(responseBody);

          if (boundMatcher.find()) {
            isBound = boundMatcher.group(1).equalsIgnoreCase("true");
          } else {
            plugin.getLogger().log(Level.WARNING,
                "Failed to parse 'bound' field for {0}. Response: {1}",
                new Object[]{playerName, responseBody});
          }

          // 2. 解析 "bindingCode" 字段
          Matcher codeMatcher = CODE_PATTERN.matcher(responseBody);

          if (codeMatcher.find()) {
            bindingCode = codeMatcher.group(1);
            if (bindingCode.equalsIgnoreCase("null")) {
              bindingCode = null;
            }
          }

          if (plugin.isDebugMode()) {
            plugin.getLogger().log(Level.INFO,
                "API response for {0}: {1}", new Object[]{playerName, responseBody});
          }

          future.complete(new BindingStatus(isBound, bindingCode));
        } else {
          plugin.getLogger().log(Level.WARNING,
              "API request failed for player {0}. Response code: {1}",
              new Object[]{playerName, responseCode});
          future.complete(new BindingStatus(false, null));
        }
      } catch (Exception e) {
        plugin.getLogger().log(Level.SEVERE,
            "Error checking binding status for player " + playerName, e);
        future.complete(new BindingStatus(false, null));
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    });

    return future;
  }

  /**
   * 构建查询绑定状态的完整 URL.
   *
   * @param playerName 玩家名称 (MCID).
   * @return 完整的 API URL 字符串.
   */
  private String buildBindingStatusUrl(String playerName) {
    // 确保 apiUrl 以斜杠结尾
    String baseUrl = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
    // 拼接新的 API 路径和查询参数
    return baseUrl + "api/getBindingStatus?mcid=" + playerName;
  }

  /**
   * 辅助方法：在主线程中向玩家发送消息.
   */
  public static void sendPlayerMessage(JavaPlugin plugin, Player player, String message) {
    if (player != null && player.isOnline()) {
      Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }
  }

  /**
   * 封装 API 响应的绑定状态和绑定码.
   */
  public static class BindingStatus {
    private final boolean isBound;
    private final String bindingCode;

    /**
     * 构造函数.
     *
     * @param isBound 是否已绑定.
     * @param bindingCode 绑定码.
     */
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