package dev.sevenclient.config;

import dev.sevenclient.SevenClient;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.ModuleManager;
import dev.sevenclient.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free flat config. One line per value:
 *   ModuleName.SettingName=value
 *   ModuleName.enabled=true
 *
 * Path is built through the Fabric config dir + resolve(), so it is correct on
 * Linux and Windows without any separator handling.
 */
public final class ConfigManager {

    private final ModuleManager modules;
    private final Path file;

    public ConfigManager(ModuleManager modules) {
        this.modules = modules;
        this.file = FabricLoader.getInstance().getConfigDir().resolve("777client").resolve("config.txt");
    }

    public Path path() {
        return file;
    }

    public void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# 777 Client config");
        for (Module m : modules.all()) {
            lines.add(m.name() + ".enabled=" + m.isEnabled());
            for (Setting<?> s : m.settings()) {
                lines.add(m.name() + "." + s.name() + "=" + s.serialize());
            }
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            SevenClient.LOG.error("Failed to save config", e);
        }
    }

    public void load() {
        if (!Files.exists(file)) {
            save();
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                int dot = line.indexOf('.');
                if (eq < 0 || dot < 0 || dot > eq) continue;

                String moduleName = line.substring(0, dot);
                String key = line.substring(dot + 1, eq);
                String value = line.substring(eq + 1);

                Module m = modules.byName(moduleName);
                if (m == null) continue;

                if (key.equals("enabled")) {
                    m.setEnabled(Boolean.parseBoolean(value));
                    continue;
                }
                for (Setting<?> s : m.settings()) {
                    if (s.name().equals(key)) {
                        s.deserialize(value);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            SevenClient.LOG.error("Failed to load config", e);
        }
    }
}
