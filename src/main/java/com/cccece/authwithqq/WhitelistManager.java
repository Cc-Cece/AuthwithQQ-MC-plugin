package com.cccece.authwithqq;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 管理豁免 QQ 绑定的玩家名单.
 */
public class WhitelistManager {
  private final AuthWithQqPlugin plugin;
  private File file;
  private FileConfiguration config;
  private final Set<String> whitelistedNames = new HashSet<>();
  private final Set<UUID> whitelistedUuids = new HashSet<>();

  /**
   * 构造函数，初始化并加载白名单文件.
   *
   * @param plugin 插件实例
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public WhitelistManager(AuthWithQqPlugin plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "whitelist.yml");
    if (!file.exists()) {
      try {
        file.createNewFile();
      } catch (IOException e) {
        plugin.getLogger().severe("无法创建 whitelist.yml!");
      }
    }
    reload();
  }

  /**
   * 重新加载白名单配置文件.
   */
  public void reload() {
    config = YamlConfiguration.loadConfiguration(file);
    whitelistedNames.clear();
    whitelistedUuids.clear();

    List<String> names = config.getStringList("names");
    if (names != null) {
      whitelistedNames.addAll(names);
    }

    List<String> uuids = config.getStringList("uuids");
    if (uuids != null) {
      for (String s : uuids) {
        try {
          whitelistedUuids.add(UUID.fromString(s));
        } catch (Exception ignored) {
          // 忽略格式错误的 UUID
        }
      }
    }
  }

  /**
   * 保存白名单数据到文件.
   */
  public void save() {
    config.set("names", new ArrayList<>(whitelistedNames));
    List<String> uuidStrings = new ArrayList<>();
    for (UUID uuid : whitelistedUuids) {
      uuidStrings.add(uuid.toString());
    }
    config.set("uuids", uuidStrings);
    try {
      config.save(file);
    } catch (IOException e) {
      plugin.getLogger().severe("无法保存 whitelist.yml!");
    }
  }

  /**
   * 添加玩家名称到白名单.
   *
   * @param name 玩家名称
   */
  public void addName(String name) {
    whitelistedNames.add(name.toLowerCase());
    save();
  }

  /**
   * 添加玩家 UUID 到白名单.
   *
   * @param uuid 玩家 UUID
   */
  public void addUuid(UUID uuid) {
    whitelistedUuids.add(uuid);
    save();
  }

  /**
   * 从白名单中移除玩家名称.
   *
   * @param name 玩家名称
   */
  public void removeName(String name) {
    whitelistedNames.remove(name.toLowerCase());
    save();
  }

  /**
   * 从白名单中移除玩家 UUID.
   *
   * @param uuid 玩家 UUID
   */
  public void removeUuid(UUID uuid) {
    whitelistedUuids.remove(uuid);
    save();
  }

  /**
   * 检查玩家是否在白名单中.
   *
   * @param name 玩家名称
   * @param uuid 玩家 UUID
   * @return 如果在白名单中返回 true
   */
  public boolean isWhitelisted(String name, UUID uuid) {
    return whitelistedNames.contains(name.toLowerCase()) || whitelistedUuids.contains(uuid);
  }
}