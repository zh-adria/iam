package com.iam.app.service;

import com.iam.app.service.ScimProvisionerTokenService;
import com.iam.infrastructure.config.DynamicConfig;
import com.iam.infrastructure.ldap.LdapConfig;
import com.iam.infrastructure.repository.AuditLogRepository;
import com.iam.infrastructure.repository.LdapGroupRoleMappingRepository;
import com.iam.infrastructure.repository.OAuth2ClientRepository;
import com.iam.infrastructure.repository.PermissionRepository;
import com.iam.infrastructure.repository.RolePermissionRepository;
import com.iam.infrastructure.repository.RoleRepository;
import com.iam.infrastructure.repository.SamlIdpRegistrationRepository;
import com.iam.infrastructure.repository.TenantRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.repository.UserRoleRepository;
import com.iam.infrastructure.security.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAppServiceTest {

    @Test
    void systemConfigReturnsDynamicConfigEntries() {
        DynamicConfig dynamicConfig = mock(DynamicConfig.class);
        List<Map<String, String>> entries = List.of(Map.of(
                "key", "iam.social.qq.app-id",
                "value", "",
                "type", "string",
                "description", "QQ OAuth client id"));
        when(dynamicConfig.listAll()).thenReturn(entries);

        AdminAppService service = new AdminAppService(
                mock(UserRepository.class),
                mock(RoleRepository.class),
                mock(PermissionRepository.class),
                mock(UserRoleRepository.class),
                mock(RolePermissionRepository.class),
                mock(TenantRepository.class),
                mock(AuditLogRepository.class),
                mock(OAuth2ClientRepository.class),
                mock(SamlIdpRegistrationRepository.class),
                dynamicConfig,
                mock(PasswordHasher.class),
                mock(LdapConfig.class),
                mock(ScimProvisionerTokenService.class),
                mock(LdapGroupRoleMappingRepository.class));

        Map<String, Object> result = service.systemConfig();

        assertEquals(entries, result.get("items"));
    }

    @Test
    void setSystemConfigDelegatesToDynamicConfig() {
        DynamicConfig dynamicConfig = mock(DynamicConfig.class);
        AdminAppService service = new AdminAppService(
                mock(UserRepository.class),
                mock(RoleRepository.class),
                mock(PermissionRepository.class),
                mock(UserRoleRepository.class),
                mock(RolePermissionRepository.class),
                mock(TenantRepository.class),
                mock(AuditLogRepository.class),
                mock(OAuth2ClientRepository.class),
                mock(SamlIdpRegistrationRepository.class),
                dynamicConfig,
                mock(PasswordHasher.class),
                mock(LdapConfig.class),
                mock(ScimProvisionerTokenService.class),
                mock(LdapGroupRoleMappingRepository.class));

        service.setSystemConfig("iam.social.qq.app-id", "qq-client", "string");

        verify(dynamicConfig).set("iam.social.qq.app-id", "qq-client", "string");
    }
}
