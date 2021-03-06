/*
 * Copyright (C) 2013 Dabo Ross <http://www.daboross.net/>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.daboross.bukkitdev.skywars.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.daboross.bukkitdev.skywars.StartupFailedException;
import net.daboross.bukkitdev.skywars.api.SkyWars;
import net.daboross.bukkitdev.skywars.api.arenaconfig.SkyArenaConfig;
import net.daboross.bukkitdev.skywars.api.config.SkyConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 *
 * @author Dabo Ross <http://www.daboross.net/>
 */
public class SkyWarsConfiguration implements SkyConfiguration {

    private final SkyWars plugin;
    private File mainConfigFile;
    private File arenaFolder;
    private FileConfiguration mainConfig;
    private SkyArenaConfig parentArena;
    private List<SkyArenaConfig> enabledArenas;
    private Map<File, String> headers;
    private ArenaOrder order;
    private String messagePrefix;

    public SkyWarsConfiguration(SkyWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public void load() {
        if (mainConfig == null) {
            reload();
        }
    }

    @Override
    public void reload() {
        if (mainConfigFile == null) {
            mainConfigFile = new File(plugin.getDataFolder(), Names.MAIN);
        }
        if (!mainConfigFile.exists()) {
            try {
                plugin.saveResource(Names.MAIN, false);
            } catch (IllegalArgumentException ex) {
                throw new StartupFailedException("Couldn't save resource " + Names.MAIN, ex);
            }
        }
        if (!mainConfigFile.exists()) {
            throw new StartupFailedException(mainConfigFile.getAbsolutePath() + " doesn't exist even after copying from jar.");
        }

        if (arenaFolder == null) {
            arenaFolder = new File(plugin.getDataFolder(), Names.ARENAS);
        }
        if (!arenaFolder.exists()) {
            boolean mkdirs = arenaFolder.mkdirs();
            if (!mkdirs) {
                throw new StartupFailedException("Making directory " + arenaFolder.getAbsolutePath() + " failed");
            }
        } else if (!arenaFolder.isDirectory()) {
            throw new StartupFailedException("File " + arenaFolder.getAbsolutePath() + " exists but is not a directory");
        }
        if (mainConfig == null) {
            mainConfig = new YamlConfiguration();
        }
        try {
            mainConfig.load(mainConfigFile);
        } catch (FileNotFoundException ex) {
            throw new StartupFailedException(mainConfigFile.getAbsolutePath() + " not found even after it is proven to exist", ex);
        } catch (IOException ex) {
            throw new StartupFailedException("IOException while loading " + mainConfigFile.getAbsolutePath(), ex);
        } catch (InvalidConfigurationException ex) {
            throw new StartupFailedException("Invalid configuration for " + mainConfigFile.getAbsolutePath(), ex);
        }

        if (mainConfig.isInt(Keys.VERSION)) {
            int version = mainConfig.getInt(Keys.VERSION);
            if (version != 0) {
                throw new StartupFailedException("Version '" + version + "' as listed under " + Keys.VERSION + " in file " + mainConfigFile.getAbsolutePath() + " is unknown.");
            }
        } else if (mainConfig.contains(Keys.VERSION)) {
            throw new StartupFailedException(getInvalid(Keys.VERSION, mainConfig.get(Keys.VERSION), mainConfigFile, "Integer"));
        } else {
            throw new StartupFailedException(Keys.VERSION + " does not exist in file " + mainConfigFile.getAbsolutePath());
        }

        if (mainConfig.isBoolean(Keys.DEBUG)) {
            SkyStatic.setDebug(mainConfig.getBoolean(Keys.DEBUG));
        } else if (mainConfig.contains(Keys.DEBUG)) {
            throw new StartupFailedException(getInvalid(Keys.DEBUG, mainConfig.get(Keys.DEBUG), mainConfigFile, "Boolean"));
        } else {
            logChange(Keys.DEBUG, Defaults.DEBUG, mainConfigFile);
            mainConfig.set(Keys.DEBUG, Defaults.DEBUG);
            SkyStatic.setDebug(Defaults.DEBUG);
        }

        if (mainConfig.isString(Keys.ARENA_ORDER)) {
            order = ArenaOrder.getOrder(mainConfig.getString(Keys.ARENA_ORDER));
            if (order == null) {
                throw new StartupFailedException("Invalid ArenaOrder '" + order + "' found under " + Keys.ARENA_ORDER + " in file " + mainConfigFile.getAbsolutePath() + ". Valid values: " + ArenaOrder.values());
            }
        } else if (mainConfig.contains(Keys.ARENA_ORDER)) {
            throw new StartupFailedException(getInvalid(Keys.ARENA_ORDER, mainConfig.get(Keys.ARENA_ORDER), mainConfigFile, "String"));
        } else {
            logChange(Keys.ARENA_ORDER, Defaults.ARENA_ORDER, mainConfigFile);
            mainConfig.set(Keys.ARENA_ORDER, Defaults.ARENA_ORDER);
            order = Defaults.ARENA_ORDER;
        }


        if (mainConfig.isString(Keys.MESSAGE_PREFIX)) {
            messagePrefix = mainConfig.getString(Keys.MESSAGE_PREFIX);
        } else if (mainConfig.contains(Keys.MESSAGE_PREFIX)) {
            throw new StartupFailedException("Value '" + mainConfig.get(Keys.MESSAGE_PREFIX) + "' that is not a string found under " + Keys.MESSAGE_PREFIX + " in file " + mainConfigFile.getAbsolutePath());
        } else {
            logChange(Keys.MESSAGE_PREFIX, Defaults.MESSAGE_PREFIX, mainConfigFile);
            mainConfig.set(Keys.MESSAGE_PREFIX, Defaults.MESSAGE_PREFIX);
            messagePrefix = Defaults.MESSAGE_PREFIX;
        }


        if (mainConfig.isList(Keys.ENABLED_ARENAS)) { // This needs to come after MESSAGE_PREFIX
            List<?> enabledArenasList = mainConfig.getList(Keys.ENABLED_ARENAS);
            if (enabledArenasList.isEmpty()) {
                throw new StartupFailedException("No enabled arenas found");
            }
            enabledArenas = new ArrayList<>(enabledArenasList.size());
            headers = new HashMap<>(enabledArenasList.size() + 1);
            loadParent();
            for (Object o : enabledArenasList) {
                if (o instanceof String) {
                    loadArena((String) o);
                } else {
                    throw new StartupFailedException(getInvalid(Keys.ENABLED_ARENAS, o, mainConfigFile, "String"));
                }
            }
        } else if (mainConfig.contains(Keys.ENABLED_ARENAS)) {
            throw new StartupFailedException(getInvalid(Keys.ENABLED_ARENAS, mainConfig.get(Keys.ENABLED_ARENAS), mainConfigFile, "list"));
        } else {
            logChange(Keys.ENABLED_ARENAS, Defaults.ENABLED_ARENAS, mainConfigFile);
            mainConfig.set(Keys.ENABLED_ARENAS, Defaults.ENABLED_ARENAS);
            enabledArenas = new ArrayList<>(0);
            throw new StartupFailedException("No enabled arenas found");
        }
    }

    private void loadArena(String name) {
        File file = new File(arenaFolder, name + ".yml");
        if (!file.exists()) {
            String fileName = Names.ARENAS + File.separatorChar + name + ".yml";
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException ex) {
                throw new StartupFailedException(name + " is in " + Keys.ENABLED_ARENAS + " but file " + file.getAbsolutePath() + " could not be found and file " + fileName + " could not be found in plugin jar.");
            }
        }
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (FileNotFoundException ex) {
            throw new StartupFailedException(name + " is in " + Keys.ENABLED_ARENAS + " but file " + file.getAbsolutePath() + " could not be found", ex);
        } catch (IOException ex) {
            throw new StartupFailedException("IOException load file " + file.getAbsolutePath(), ex);
        } catch (InvalidConfigurationException ex) {
            throw new StartupFailedException("Failed to load configuration file " + file.getAbsolutePath(), ex);
        }
        SkyArenaConfig arenaConfig = SkyArenaConfig.deserialize(config);
        arenaConfig.setArenaName(name);
        arenaConfig.setFile(file);
        arenaConfig.getMessages().setPrefix(messagePrefix);
        arenaConfig.setParent(parentArena);
        headers.put(file, config.options().header());
        enabledArenas.add(arenaConfig);
    }

    private void loadParent() {
        File file = new File(plugin.getDataFolder(), "arena-parent.yml");
        if (!file.exists()) {
            String fileName = "arena-parent.yml";
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException ex) {
                throw new StartupFailedException("arena-parent.yml could not be found in plugin jar.", ex);
            }
        }
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (FileNotFoundException ex) {
            throw new StartupFailedException("Can't find the parent arena yaml", ex);
        } catch (IOException ex) {
            throw new StartupFailedException("IOException loading arena-parent " + file.getAbsolutePath(), ex);
        } catch (InvalidConfigurationException ex) {
            throw new StartupFailedException("Failed to load arena-parent.yml " + file.getAbsolutePath(), ex);
        }
        SkyArenaConfig arenaConfig = SkyArenaConfig.deserialize(config);
        arenaConfig.setArenaName("parent-arena");
        arenaConfig.setFile(file);
        arenaConfig.getMessages().setPrefix(messagePrefix);
        headers.put(file, config.options().header());
        parentArena = arenaConfig;
    }

    @Override
    public void save() {
        if (mainConfig != null) {
            try {
                mainConfig.save(mainConfigFile);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Couldn't save main-config.yml", ex);
            }
            for (SkyArenaConfig config : enabledArenas) {
                File file = config.getFile();
                FileConfiguration fileConfig = new YamlConfiguration();
                fileConfig.options().header(headers.get(file));
                config.serialize(fileConfig);
                try {
                    fileConfig.save(file);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save arena config to file " + file.getAbsolutePath(), ex);
                }
            }
        }
    }

    @Override
    public List<SkyArenaConfig> getEnabledArenas() {
        load();
        return Collections.unmodifiableList(enabledArenas);
    }

    @Override
    public ArenaOrder getArenaOrder() {
        return order;
    }

    @Override
    public String getMessagePrefix() {
        load();
        return messagePrefix;
    }

    private void logChange(String key, Object value, File config) {
        plugin.getLogger().log(Level.WARNING, "Setting {0} to {1} in {2}", new Object[]{key, value, config.getAbsolutePath()});
    }

    private String getInvalid(String key, Object value, File file, String shouldBe) {
        return "Object '" + value + "' that isn't a " + shouldBe + " found under " + key + " in file " + file.getAbsolutePath();
    }

    private static class Keys {

        private static final String VERSION = "config-version";
        private static final String ENABLED_ARENAS = "enabled-arenas";
        private static final String ARENA_ORDER = "arena-order";
        private static final String MESSAGE_PREFIX = "message-prefix";
        private static final String DEBUG = "debug";
    }

    private static class Names {

        private static final String MAIN = "main-config.yml";
        private static final String ARENAS = "arenas";
    }

    private static class Defaults {

        private static final String MESSAGE_PREFIX = "&8[&cSkyWars&8]#B ";
        private static final Boolean DEBUG = Boolean.FALSE;
        private static final ArenaOrder ARENA_ORDER = ArenaOrder.RANDOM;
        private static final List<?> ENABLED_ARENAS = Collections.EMPTY_LIST;
    }
}
