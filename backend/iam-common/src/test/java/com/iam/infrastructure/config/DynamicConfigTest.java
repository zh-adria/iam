package com.iam.infrastructure.config;

import com.iam.infrastructure.repository.ConfigItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicConfigTest {

    @Test
    void initDoesNotFailWhenRedisIsUnavailable() {
        ConfigItemRepository repo = mock(ConfigItemRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(repo.findAll()).thenReturn(List.of());
        when(redis.getConnectionFactory()).thenThrow(new RedisConnectionFailureException("redis down"));

        DynamicConfig dynamicConfig = new DynamicConfig(repo, redis);

        assertDoesNotThrow(dynamicConfig::init);
    }
}
