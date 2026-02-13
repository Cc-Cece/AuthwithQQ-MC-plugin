package com.crimsonwarpedcraft.qqbindingguestmode;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * 处理玩家加入事件和交互限制事件。
 */
public class BindingListener implements Listener {

  private final QQBindingGuestModePlugin plugin;

  /**
   * 构造函数。
   * @param plugin 插件实例。
   */
  public BindingListener(QQBindingGuestModePlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * 玩家加入事件：异步检查绑定状态并设置游戏模式。
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    // 异步检查绑定状态
    plugin.getBindingAPI().getBindingStatusAsync(player.getName())
        .thenAccept(status -> {
          // 确保在主线程中执行 Bukkit API 调用
          plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (status.isBound()) {
              // 已绑定：确保是 Survival 模式
              if (player.getGameMode() != GameMode.SURVIVAL) {
                player.setGameMode(GameMode.SURVIVAL);
              }
            } else {
              // 未绑定：强制 Adventure 模式并发送提示
              if (player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
              }
              
              String promptMessage = plugin.getUnboundPromptMessage();
              if (status.getBindingCode() != null) {
                promptMessage = promptMessage.replace("{CODE}", status.getBindingCode());
              }
              BindingAPI.sendPlayerMessage(plugin, player, promptMessage);
              
              // 将玩家添加到轮询列表
              plugin.getPlayersToPoll().add(player.getName());
            }
          });
        });
  }

  /**
   * 检查玩家是否处于 Adventure 模式，如果是，则取消事件并发送提示。
   *
   * @param player 玩家。
   * @param event 可取消的事件。
   */
  private void checkRestriction(Player player, org.bukkit.event.Cancellable event) {
    if (player.getGameMode() == GameMode.ADVENTURE) {
      event.setCancelled(true);
      
      // 异步获取绑定码并发送限制消息
      plugin.getBindingAPI().getBindingStatusAsync(player.getName())
          .thenAccept(status -> {
            if (!status.isBound()) {
              String restrictionMessage = plugin.getUnboundRestrictionMessage();
              if (status.getBindingCode() != null) {
                restrictionMessage = restrictionMessage.replace("{CODE}", status.getBindingCode());
              }
              final String finalRestrictionMessage = restrictionMessage;
              // 确保在主线程发送消息
              plugin.getServer().getScheduler().runTask(plugin, () -> {
                BindingAPI.sendPlayerMessage(plugin, player, finalRestrictionMessage);
              });
            }
            // 如果已绑定，但仍处于 ADVENTURE 模式，轮询任务会很快修复，这里不发送消息。
          });
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onBlockBreak(BlockBreakEvent event) {
    checkRestriction(event.getPlayer(), event);
  }

  /**
   * 监听 BlockPlaceEvent。
   */
  @EventHandler(priority = EventPriority.HIGH)
  public void onBlockPlace(BlockPlaceEvent event) {
    checkRestriction(event.getPlayer(), event);
  }

  /**
   * 监听 PlayerDropItemEvent。
   */
  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    checkRestriction(event.getPlayer(), event);
  }

  /**
   * 监听 PlayerPickupItemEvent。
   */
  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerPickupItem(PlayerPickupItemEvent event) {
    checkRestriction(event.getPlayer(), event);
  }

  /**
   * 监听 PlayerInteractEvent。
   */
  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerInteract(PlayerInteractEvent event) {
    // PlayerInteractEvent 包含多种交互，如开箱子/门。
    // 仅在 Adventure 模式下限制
    checkRestriction(event.getPlayer(), event);
  }
}