package com.cccece.authwithqq.velocity;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理与外部 QQ 绑定 API 的通信 (Velocity 代理端).
 */
public class VelocityBindingApi {

  private static final Pattern BOUND_PATTERN = Pattern.compile(
      "\"bound\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CODE_PATTERN = Pattern.compile(
      "\"bindingCode\"\\s*:\\s*\"?([^\",]*)\"?", Pattern.CASE_INSENSITIVE);

  private final String apiUrl;
  private final Logger logger;
  private final boolean debugMode;

  /**
   * 构造函数.
   *
   * @param apiUrl    绑定 API 的基础 URL.
   * @param logger    日志记录器.
   * @param debugMode 是否启用调试模式.
   */
  public VelocityBindingApi(String apiUrl, Logger logger, boolean debugMode) {
    this.apiUrl = apiUrl;
    this.logger = logger;
    this.debugMode = debugMode;
  }

  /**
   * 异步检查玩家是否已绑定 QQ.
   *
   * @param playerName 玩家名称 (MCID).
   * @return CompletableFuture 包含绑定状态.
   */
  public CompletableFuture<BindingStatus> getBindingStatusAsync(String playerName) {
    return CompletableFuture.supplyAsync(() -> getBindingStatus(playerName));
  }

  /**
   * 同步检查玩家是否已绑定 QQ.
   *
   * @param playerName 玩家名称 (MCID).
   * @return 绑定状态对象.
   */
  @SuppressFBWarnings({"URLCONNECTION_SSRF_FD", "CRLF_INJECTION_LOGS"})
  public BindingStatus getBindingStatus(String playerName) {
    String urlString = buildBindingStatusUrl(playerName);
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

        String responseBody = response.toString();
        boolean isBound = false;
        String bindingCode = null;

        Matcher boundMatcher = BOUND_PATTERN.matcher(responseBody);
        if (boundMatcher.find()) {
          isBound = boundMatcher.group(1).equalsIgnoreCase("true");
        } else {
          logger.log(Level.WARNING, "Failed to parse ''bound'' field for {0}.", playerName);
        }

        Matcher codeMatcher = CODE_PATTERN.matcher(responseBody);
        if (codeMatcher.find()) {
          bindingCode = codeMatcher.group(1);
          if (bindingCode != null && bindingCode.equalsIgnoreCase("null")) {
            bindingCode = null;
          }
        }

        if (debugMode) {
          logger.log(Level.INFO, "API response for {0} received.", playerName);
        }

        return new BindingStatus(isBound, bindingCode);
      } else {
        logger.log(Level.WARNING,
            "API request failed for player {0}. Response code: {1}",
            new Object[]{playerName, responseCode});
        return new BindingStatus(false, null);
      }
    } catch (IOException | RuntimeException e) {
      logger.log(Level.SEVERE,
          "Error checking binding status for player " + playerName, e);
      return new BindingStatus(false, null);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * 构建查询绑定状态的完整 URL.
   *
   * @param playerName 玩家名称 (MCID).
   * @return 完整的 API URL 字符串.
   */
  private String buildBindingStatusUrl(String playerName) {
    String baseUrl = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
    return baseUrl + "api/getBindingStatus?mcid=" + playerName;
  }

  /**
   * 封装 API 响应的绑定状态和绑定码.
   */
  public static class BindingStatus {
    private final boolean bound;
    private final String bindingCode;

    /**
     * 构造函数.
     *
     * @param bound       是否已绑定.
     * @param bindingCode 绑定码 (未绑定时为玩家专属绑定标识).
     */
    public BindingStatus(boolean bound, String bindingCode) {
      this.bound = bound;
      this.bindingCode = bindingCode;
    }

    public boolean isBound() {
      return bound;
    }

    public String getBindingCode() {
      return bindingCode;
    }
  }
}
