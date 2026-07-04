package com.iam.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class EncryptedPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DECRYPTED_PROPERTY_SOURCE = "decryptedEncryptedProperties";
    private static final String CONFIG_KEY_PROPERTY = "iam.config-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> decrypted = new LinkedHashMap<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String propertyName : enumerable.getPropertyNames()) {
                Object value = enumerable.getProperty(propertyName);
                if (value instanceof String text && EncryptedPropertyCipher.isEncrypted(text)) {
                    decrypted.put(propertyName, EncryptedPropertyCipher.decrypt(text, resolveConfigKey(environment)));
                }
            }
        }
        if (!decrypted.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(DECRYPTED_PROPERTY_SOURCE, decrypted));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private String resolveConfigKey(ConfigurableEnvironment environment) {
        String key = environment.getProperty(CONFIG_KEY_PROPERTY);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Encrypted configuration requires IAM_CONFIG_KEY or iam.config-key");
        }
        return key;
    }
}
