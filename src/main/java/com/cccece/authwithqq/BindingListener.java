package com.cccece.authwithqq;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 监听玩家行为，拦截未绑定 QQ 的玩家操作.
 */
public class BindingListener implements Listener {

  private final AuthWithQqPlugin plugin;
  private final Set<UUID> unverifiedPlayers;

  /**
   * 构造函数.
   *
   * @param plugin 插件实例
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public BindingListener(AuthWithQqPlugin plugin) {
    this.plugin = plugin;
    this.unverifiedPlayers = Collections.synchronizedSet(new HashSet<>());
  }

  /**
   * 监听玩家加入事件.
   *
   * @param event 加入事件
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    String name = player.getName();

    // --- 核心修改：白名单豁免逻辑 ---
    if (plugin.getWhitelistManager().isWhitelisted(name, uuid)) {
      if (plugin.isDebugMode()) {
        plugin.getLogger().info("玩家 " + name + " (UUID: " + uuid + ") 在豁免名单中，跳过验证。");
      }
      player.setGameMode(GameMode.SURVIVAL);
      return; // 豁免玩家直接跳过后续所有逻辑
    }
    // ----------------------------

    // 非豁免玩家，先限制为冒险模式并加入验证列表
    player.setGameMode(GameMode.ADVENTURE);
    unverifiedPlayers.add(uuid);

    // 异步查询 API 绑定状态
    plugin.getBindingApi().getBindingStatusAsync(name).thenAccept(status -> {
      if (status.isBound()) {
        // 如果已绑定，切换回生存模式并移除限制
        plugin.getServer().getScheduler().runTask(plugin, () -> {
          plugin.handleBindingSuccess(player);
        });
      } else {
        // 如果未绑定，发送提示并加入轮询队列
        BindingApi.sendPlayerMessage(plugin, player, plugin.getUnboundPromptMessage());
        plugin.getPlayersToPoll().add(name);
      }
    }).exceptionally(ex -> {
      plugin.getLogger().severe("查询玩家 " + name + " 绑定状态时出错: " + ex.getMessage());
      return null;
    });
  }

  /**
   * 监听玩家退出事件.
   *
   * @param event 退出事件
   */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    unverifiedPlayers.remove(uuid);
    plugin.getPlayersToPoll().remove(event.getPlayer().getName());
  }

  /**
   * 标记玩家为已验证.
   *
   * @param uuid 玩家 UUID
   */
  public void markVerified(UUID uuid) {
    unverifiedPlayers.remove(uuid);
  }

  // --- 拦截逻辑 ---

  private boolean isUnverified(Player player) {
    return unverifiedPlayers.contains(player.getUniqueId());
  }

  /**
   * 拦截未验证玩家的移动.
   *
   * @param event 移动事件
   */
  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    if (plugin.isRestrictMovement() && isUnverified(event.getPlayer())) {
      // 允许转头，但不允许移动坐标
      if (event.getFrom().getX() != event.getTo().getX()
          || event.getFrom().getZ() != event.getTo().getZ()) {
        event.setTo(event.getFrom());
        BindingApi.sendPlayerMessage(plugin, event.getPlayer(),
            plugin.getUnboundRestrictionMessage());
      }
    }
  }

  /**
   * 拦截未验证玩家的世界切换（传送）.
   *
   * @param event 传送事件
   */
  @EventHandler
  public void onTeleport(PlayerTeleportEvent event) {
    if (plugin.isRestrictWorldChange() && isUnverified(event.getPlayer())) {
      if (event.getFrom().getWorld() != event.getTo().getWorld()) {
        event.setCancelled(true);
        BindingApi.sendPlayerMessage(plugin, event.getPlayer(),
            plugin.getUnboundRestrictionMessage());
      }
    }
  }

  /**
   * 拦截未验证玩家的世界切换（通过门户）.
   *
   * @param event 世界切换事件
   */
  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent event) {
    // 这个事件无法被取消，但可以把玩家传回原处
    // 但通常 PlayerTeleportEvent 会先拦截跨世界传送
  }

  /**
   * 拦截未验证玩家的交互.
   *
   * @param event 交互事件
   */
  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    if (plugin.isRestrictInteraction() && isUnverified(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  /**
   * 拦截未验证玩家的破坏.
   *
   * @param event 破坏事件
   */
  @EventHandler
  public void onBreak(BlockBreakEvent event) {
    if (plugin.isRestrictBlockBreak() && isUnverified(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  /**
   * 拦截未验证玩家的放置.
   *
   * @param event 放置事件
   */
  @EventHandler
  public void onPlace(BlockPlaceEvent event) {
    if (plugin.isRestrictBlockPlace() && isUnverified(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  /**
   * 拦截未验证玩家的聊天.
   *
   * @param event 聊天事件
   */
  @EventHandler
  public void onChat(AsyncPlayerChatEvent event) {
    if (isUnverified(event.getPlayer())) {
      event.setCancelled(true);
      BindingApi.sendPlayerMessage(plugin, event.getPlayer(),
          plugin.getUnboundRestrictionMessage());
    }
  }

  /**
   * 拦截未验证玩家的指令.
   *
   * @param event 指令事件
   */
  @EventHandler
  public void onCommand(PlayerCommandPreprocessEvent event) {
    if (isUnverified(event.getPlayer())) {
      String cmd = event.getMessage().toLowerCase();
      // 允许基本的登录/注册命令
      if (cmd.startsWith("/login ") || cmd.startsWith("/register ") || cmd.startsWith("/l ")) {
        return;
      }
      event.setCancelled(true);
      BindingApi.sendPlayerMessage(plugin, event.getPlayer(),
          plugin.getUnboundRestrictionMessage());
    }
  }

  /**
   * 拦截未验证玩家的丢弃物品.
   *
   * @param event 丢弃事件
   */
  @EventHandler
  public void onDrop(PlayerDropItemEvent event) {
    if (isUnverified(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  /**
   * 拦截未验证玩家的捡起物品.
   *
   * @param event 捡起事件
   */
  @EventHandler
  public void onPickup(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player) {
      if (isUnverified((Player) event.getEntity())) {
        event.setCancelled(true);
      }
    }
  }

  /**
   * 拦截未验证玩家的伤害.
   *
   * @param event 伤害事件
   */
  @EventHandler
  public void onDamage(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Player) {
      if (isUnverified((Player) event.getDamager())) {
        event.setCancelled(true);
      }
    }
  }
}