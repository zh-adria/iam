package com.iam.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedPropertyEnvironmentPostProcessorTest {

    @Test
    void decryptsEncryptedEnumerablePropertiesIntoHighPriorityPropertySource() {
        String key = "test-config-key";
        String encryptedUrl = EncryptedPropertyCipher.encrypt("jdbc:mysql://example/dev", key);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-source", Map.of(
                "iam.config-key", key,
                "spring.datasource.url", encryptedUrl,
                "spring.datasource.username", "plain-user"
        )));

        new EncryptedPropertyEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://example/dev");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("plain-user");
    }
}
