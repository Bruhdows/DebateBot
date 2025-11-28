package com.bruhdows.debatebot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static <T> Optional<T> loadConfig(Class<T> configClass, String fileName) {
        Path path = Path.of(fileName);
        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                T config = GSON.fromJson(reader, configClass);
                return Optional.ofNullable(config);
            } catch (IOException e) {
                LOGGER.error("An error occured when loading {}: ", fileName, e);
            }
        }
        return Optional.empty();
    }

    public static <T> T register(Class<T> configClass, String fileName) {
        Optional<T> loadedConfig = loadConfig(configClass, fileName);

        T config;
        Path path = Path.of(fileName);
        if (loadedConfig.isPresent()) {
            config = loadedConfig.get();
            T defaults = createDefault(configClass);
            mergeMissingFields(config, defaults);
            saveConfig(config, path);
        } else {
            config = createDefault(configClass);
            saveConfig(config, path);
        }
        return config;
    }

    private static <T> T createDefault(Class<T> configClass) {
        try {
            return configClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default config instance", e);
        }
    }

    private static <T> void mergeMissingFields(T target, T defaults) {
        try {
            Field[] fields = target.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object targetValue = field.get(target);
                if (targetValue == null || (targetValue instanceof String && ((String) targetValue).isEmpty())) {
                    Object defaultValue = field.get(defaults);
                    field.set(target, defaultValue);
                }
            }
        } catch (Exception e) {
            System.err.println("Config merge failed: " + e.getMessage());
        }
    }

    public static <T> void saveConfig(T config, Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileWriter writer = new FileWriter(path.toFile())) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("An error occured when saving {}: ", path, e);
        }
    }
}
