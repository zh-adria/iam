package com.iam.app.service;

import com.iam.infrastructure.entity.ApiKeyEntity;
import com.iam.infrastructure.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class ApiKeyServiceTest {

    private ApiKeyRepository repo;
    private ApiKeyService service;

    @BeforeEach
    void setup() {
        repo = Mockito.mock(ApiKeyRepository.class);
        service = new ApiKeyService(repo);
    }

    // ---------- create ----------

    @Test
    void createKey_generatesRawKey() {
        Mockito.when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.createKey("live", "my-key", "admin", "default", "read,write", 0);
        assertEquals("live", result.get("prefix"));
        String raw = (String) result.get("key");
        assertNotNull(raw);
        assertTrue(raw.startsWith("live_"), "raw key must start with prefix");
        assertEquals(37, raw.length(), "live_ + 32 hex chars");
    }

    @Test
    void createKey_rejectsUnknownPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createKey("unknown", "x", "a", "default", "read", 0));
    }

    // ---------- validate ----------

    @Test
    void validate_rejectsDisabledKey() {
        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(1L); e.setPrefix("live"); e.setEnabled(false);
        e.setExpiresAt(Instant.ofEpochMilli(Long.MAX_VALUE));
        e.setKeyHash(hash("live_" + "a".repeat(32)));
        Mockito.when(repo.findByKeyHash(any())).thenReturn(Optional.of(e));

        assertNull(service.validate("live_" + "a".repeat(32)));
    }

    @Test
    void validate_rejectsExpiredKey() {
        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(1L); e.setPrefix("live"); e.setEnabled(true);
        e.setExpiresAt(Instant.ofEpochMilli(0L)); // already expired
        e.setKeyHash(hash("live_" + "a".repeat(32)));
        Mockito.when(repo.findByKeyHash(any())).thenReturn(Optional.of(e));

        assertNull(service.validate("live_" + "a".repeat(32)));
    }

    @Test
    void validate_acceptsValidKey() {
        String rawKey = "live_" + "a".repeat(32);
        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(1L); e.setPrefix("live"); e.setEnabled(true);
        e.setExpiresAt(Instant.ofEpochMilli(Long.MAX_VALUE));
        e.setKeyHash(hash(rawKey));
        Mockito.when(repo.findByKeyHash(hash(rawKey))).thenReturn(Optional.of(e));
        Mockito.when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyEntity found = service.validate(rawKey);
        assertNotNull(found);
        assertEquals(1L, found.getId());
    }

    // ---------- revoke ----------

    @Test
    void revokeKey_setsDisabled() {
        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(1L); e.setEnabled(true);
        Mockito.when(repo.findById(1L)).thenReturn(Optional.of(e));
        Mockito.when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeKey(1L);
        assertFalse(e.getEnabled());
    }

    // ---------- list ----------

    @Test
    void listKeys_returnsAll() {
        List<ApiKeyEntity> keys = List.of(
                ApiKeyEntity.builder().id(1L).prefix("live").name("k1").build(),
                ApiKeyEntity.builder().id(2L).prefix("test").name("k2").build());
        Mockito.when(repo.findAll()).thenReturn(keys);

        List<Map<String, Object>> rows = service.listKeys();
        assertEquals(2, rows.size());
        assertEquals("live", rows.get(0).get("prefix"));
    }

    // ---------- helpers ----------

    private static String hash(String s) {
        try {
            return bytesToHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
