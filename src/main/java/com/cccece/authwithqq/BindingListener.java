package com.cccece.authwithqq;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 玩家权限与跨世界访问限制监听器.
 */
public class BindingListener implements Listener {

    private final AuthWithQqPlugin plugin;
    private final Set<UUID> unverifiedPlayers = new HashSet<>();
    
    // 硬编码的大厅配置
    private final String LOBBY_NAME = "Lobby";

    public BindingListener(AuthWithQqPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取硬编码的大厅出生点坐标.
     */
    private Location getLobbySpawn() {
        World world = Bukkit.getWorld(LOBBY_NAME);
        if (world == null) return null;
        // 在此处修改你的具体坐标参数: world, x, y, z, yaw, pitch
        return new Location(world, -110, 1, 20, 0f, 0f);
    }

    public Set<UUID> getUnverifiedPlayers() {
        return unverifiedPlayers;
    }

    /**
     * 在绑定成功后解除未验证状态.
     */
    public void markVerified(UUID uuid) {
        unverifiedPlayers.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        unverifiedPlayers.add(uuid);

        plugin.getBindingApi().getBindingStatusAsync(player.getName())
            .thenAccept(status -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (status.isBound()) {
                        unverifiedPlayers.remove(uuid);
                        if (player.getGameMode() != GameMode.SURVIVAL) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                    } else {
                        if (player.getGameMode() != GameMode.ADVENTURE) {
                            player.setGameMode(GameMode.ADVENTURE);
                        }
                        // 初始检查：如果玩家上线时不在大厅，直接拉回
                        if (!player.getWorld().getName().equalsIgnoreCase(LOBBY_NAME)) {
                            Location spawn = getLobbySpawn();
                            if (spawn != null) player.teleport(spawn);
                        }
                        
                        String prompt = plugin.getUnboundPromptMessage();
                        if (status.getBindingCode() != null) {
                            prompt = prompt.replace("{CODE}", status.getBindingCode());
                        }
                        BindingApi.sendPlayerMessage(plugin, player, prompt);
                        plugin.getPlayersToPoll().add(player.getName());
                    }
                });
            });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unverifiedPlayers.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 拦截跨世界传送 (含指令、插件、传送门)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handleWorldTransfer(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        handleWorldTransfer(event);
    }

    private void handleWorldTransfer(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!unverifiedPlayers.contains(player.getUniqueId())) return;

        // 获取目标世界
        World toWorld = event.getTo() != null ? event.getTo().getWorld() : null;
        if (toWorld == null) return;

        // 如果试图离开大厅
        if (!toWorld.getName().equalsIgnoreCase(LOBBY_NAME)) {
            event.setCancelled(true);
            
            // 将玩家坐标修正回大厅硬编码坐标
            Location lobbySpawn = getLobbySpawn();
            if (lobbySpawn != null) {
                // 如果当前玩家已经在大厅外，强制拉回；如果在内，仅取消传送
                if (!player.getWorld().getName().equalsIgnoreCase(LOBBY_NAME)) {
                    player.teleport(lobbySpawn);
                }
            }
            
            player.sendMessage("§e[!] 你必须先完成绑定才能离开大厅世界！按T在消息记录中查看你的绑定码。");
        }
    }

    private void checkRestriction(Player player, Cancellable event) {
        if (unverifiedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            BindingApi.sendPlayerMessage(plugin, player, "§e[!] 请先完成 QQ 绑定后再进行此操作！按T在消息记录中查看你的绑定码。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        checkRestriction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        checkRestriction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        checkRestriction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            checkRestriction((Player) event.getEntity(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        checkRestriction(event.getPlayer(), event);
    }
}