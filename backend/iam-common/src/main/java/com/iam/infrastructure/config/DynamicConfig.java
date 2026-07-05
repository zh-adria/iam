package com.iam.infrastructure.config;

import com.iam.infrastructure.entity.ConfigItemEntity;
import com.iam.infrastructure.repository.ConfigItemRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Component
public class DynamicConfig {

    private final ConfigItemRepository repo;
    private final StringRedisTemplate redis;
    private final Map<String, ConfigEntry> cache = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, String>> listeners = new ArrayList<>();
    private static final String CHANNEL = "iam:config:changed";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicConfig.class);

    public DynamicConfig(ConfigItemRepository repo, StringRedisTemplate redis) {
        this.repo = repo;
        this.redis = redis;
    }

    @PostConstruct
    public void init() {
        repo.findAll().forEach(e -> cache.put(e.getKey(), new ConfigEntry(e.getValue(), e.getType())));
        log.info("DynamicConfig loaded {} entries", cache.size());
        startRedisSubscription();
    }

    public String getString(String key, String def) {
        ConfigEntry e = cache.get(key);
        return e != null ? e.value : def;
    }

    public int getInt(String key, int def) {
        ConfigEntry e = cache.get(key);
        if (e == null) return def;
        try { return Integer.parseInt(e.value); } catch (Exception ex) { return def; }
    }

    public long getLong(String key, long def) {
        ConfigEntry e = cache.get(key);
        if (e == null) return def;
        try { return Long.parseLong(e.value); } catch (Exception ex) { return def; }
    }

    public boolean getBoolean(String key, boolean def) {
        ConfigEntry e = cache.get(key);
        if (e == null) return def;
        return Boolean.parseBoolean(e.value);
    }

    public synchronized void set(String key, String value, String type) {
        ConfigItemEntity e = repo.findByKey(key).orElseGet(() -> new ConfigItemEntity(key, value, type, null));
        e.setValue(value);
        e.setType(type);
        repo.save(e);
        cache.put(key, new ConfigEntry(value, type));
        publishChange(key);
        for (BiConsumer<String, String> l : listeners) l.accept(key, value);
        log.info("DynamicConfig updated: {} = {} ({})", key, value, type);
    }

    public void onChange(BiConsumer<String, String> listener) {
        listeners.add(listener);
    }

    public List<Map<String, String>> listAll() {
        List<ConfigItemEntity> all = new ArrayList<>(repo.findAll());
        all.sort(Comparator.comparing(ConfigItemEntity::getKey));
        List<Map<String, String>> result = new ArrayList<>();
        for (ConfigItemEntity e : all) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", e.getKey());
            m.put("value", e.getValue());
            m.put("type", e.getType());
            m.put("description", e.getDescription());
            result.add(m);
        }
        return result;
    }

    public void remove(String key) {
        repo.findByKey(key).ifPresent(e -> { repo.delete(e); cache.remove(key); });
    }

    private void reloadFromDb() {
        Map<String, ConfigEntry> fresh = new ConcurrentHashMap<>();
        repo.findAll().forEach(e -> fresh.put(e.getKey(), new ConfigEntry(e.getValue(), e.getType())));
        cache.clear();
        cache.putAll(fresh);
        log.info("DynamicConfig reloaded {} entries", cache.size());
    }

    private void startRedisSubscription() {
        Thread listener = new Thread(() -> {
            try {
                redis.getConnectionFactory().getConnection().subscribe(
                        (message, pattern) -> reloadFromDb(), CHANNEL.getBytes());
            } catch (Exception e) {
                log.warn("DynamicConfig Redis subscription disabled: {}", e.getMessage());
            }
        }, "iam-dynamic-config-listener");
        listener.setDaemon(true);
        listener.start();
    }

    private void publishChange(String key) {
        try {
            redis.convertAndSend(CHANNEL, key);
        } catch (Exception e) {
            log.warn("DynamicConfig Redis publish failed for {}: {}", key, e.getMessage());
        }
    }

    public void seedIfAbsent(List<ConfigValueSpec> specs) {
        for (ConfigValueSpec spec : specs) {
            if (repo.findByKey(spec.key).isEmpty()) {
                ConfigItemEntity e = new ConfigItemEntity(spec.key, spec.defaultValue, spec.type, spec.description);
                repo.save(e);
                cache.put(spec.key, new ConfigEntry(spec.defaultValue, spec.type));
                log.info("DynamicConfig seeded: {} = {} ({})", spec.key, spec.defaultValue, spec.type);
            }
        }
    }

    private record ConfigEntry(String value, String type) {}

    public static class ConfigValueSpec {
        public final String key;
        public final String type;
        public final String defaultValue;
        public final String description;
        public ConfigValueSpec(String key, String type, String defaultValue, String description) {
            this.key = key; this.type = type; this.defaultValue = defaultValue; this.description = description;
        }
    }
}
