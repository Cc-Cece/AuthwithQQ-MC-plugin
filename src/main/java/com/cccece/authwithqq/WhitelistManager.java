package com.cccece.authwithqq;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class WhitelistManager {
    private final AuthWithQqPlugin plugin;
    private File file;
    private FileConfiguration config;
    private final Set<String> whitelistedNames = new HashSet<>();
    private final Set<UUID> whitelistedUuids = new HashSet<>();

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

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        whitelistedNames.clear();
        whitelistedUuids.clear();
        
        List<String> names = config.getStringList("names");
        if (names != null) whitelistedNames.addAll(names);
        
        List<String> uuids = config.getStringList("uuids");
        if (uuids != null) {
            for (String s : uuids) {
                try { whitelistedUuids.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        config.set("names", new java.util.ArrayList<>(whitelistedNames));
        java.util.List<String> uuidStrings = new java.util.ArrayList<>();
        for (UUID uuid : whitelistedUuids) uuidStrings.add(uuid.toString());
        config.set("uuids", uuidStrings);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存 whitelist.yml!");
        }
    }

    public void addName(String name) { whitelistedNames.add(name.toLowerCase()); save(); }
    public void addUuid(UUID uuid) { whitelistedUuids.add(uuid); save(); }
    public void removeName(String name) { whitelistedNames.remove(name.toLowerCase()); save(); }
    public void removeUuid(UUID uuid) { whitelistedUuids.remove(uuid); save(); }

    public boolean isWhitelisted(String name, UUID uuid) {
        return whitelistedNames.contains(name.toLowerCase()) || whitelistedUuids.contains(uuid);
    }
}