package com.iam.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedPropertyCipherTest {

    @Test
    void encryptsAndDecryptsEncValuesWithExternalKey() {
        String encrypted = EncryptedPropertyCipher.encrypt("jdbc:mysql://example/dev", "test-config-key");

        assertThat(encrypted).startsWith("ENC(v1:");
        assertThat(EncryptedPropertyCipher.decrypt(encrypted, "test-config-key"))
                .isEqualTo("jdbc:mysql://example/dev");
    }

    @Test
    void rejectsWrongDecryptKey() {
        String encrypted = EncryptedPropertyCipher.encrypt("secret-value", "test-config-key");

        assertThatThrownBy(() -> EncryptedPropertyCipher.decrypt(encrypted, "wrong-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to decrypt");
    }
}
