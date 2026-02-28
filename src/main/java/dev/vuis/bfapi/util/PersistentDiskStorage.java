package dev.vuis.bfapi.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class PersistentDiskStorage {
    private static final Path FILE = Path.of(EnvironmentConfigs.PERSISTENT_STORAGE_LOCATION);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final transient Object lock = new Object();
    
    private static PersistentDiskStorage instance;
    private String MSrefreshToken = null;
    
    private PersistentDiskStorage() {
    }
    
    public static PersistentDiskStorage getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    public void save() {
        synchronized (lock) {
            try {
                Files.writeString(FILE, GSON.toJson(this));
            } catch (IOException e) {
                throw new RuntimeException("Failed to save to Persistent Storage", e);
            }
        }
    }

    public void setRefreshToken(String refreshToken) {
        if (refreshToken != null && refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token cannot be blank");
        }
        synchronized (lock) {
            this.MSrefreshToken = refreshToken;
            save();
        }
    }
    
    public String getMSRefreshToken() {
        return MSrefreshToken;
    }

    private static PersistentDiskStorage load() {
        try {
            if (Files.exists(FILE)) {
                String content = Files.readString(FILE);
                PersistentDiskStorage loaded = GSON.fromJson(content, PersistentDiskStorage.class);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (IOException e) {
            log.warn("Couldn't load Persistent Storage {}", e);
        }
        return new PersistentDiskStorage();
    }
    
    public void clear() {
        synchronized (lock) {
            MSrefreshToken = null;
            try {
                Files.deleteIfExists(FILE);
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}