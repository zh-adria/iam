package com.iam;

import com.iam.app.dto.TokenResponse;
import com.iam.app.service.LdapAuthService;
import com.iam.infrastructure.ldap.LdapConfig;
import com.iam.infrastructure.security.TokenCacheService;
import com.iam.start.DemoSeeder;
import com.iam.start.IamAuthServerApplication;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFReader;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = IamAuthServerApplication.class)
@ActiveProfiles("test")
class LdapAuthTest {

    private static InMemoryDirectoryServer ds;
    private static int port;

    @Autowired LdapAuthService ldapAuth;
    @Autowired LdapConfig ldapConfig;
    @Autowired DemoSeeder seeder;
    @MockBean TokenCacheService cache;

    @BeforeAll
    static void startLdap() throws Exception {
        InMemoryDirectoryServerConfig cfg = new InMemoryDirectoryServerConfig("dc=iam,dc=local");
        cfg.setSchema(null);
        // require auth so wrong-password actually fails the bind
        cfg.setAuthenticationRequiredOperationTypes(
                java.util.EnumSet.of(com.unboundid.ldap.sdk.OperationType.BIND));
        // ponytail: fixed port so test can reconfigure before @PostConstruct actually wires it.
        cfg.setListenerConfigs(com.unboundid.ldap.listener.InMemoryListenerConfig.createLDAPConfig("test", 33889));
        ds = new InMemoryDirectoryServer(cfg);
        ds.startListening();
        port = ds.getListenPort();
        String ldif =
            "dn: dc=iam,dc=local\n" +
            "objectClass: domain\n" +
            "objectClass: top\n" +
            "dc: iam\n\n" +
            "dn: ou=people,dc=iam,dc=local\n" +
            "objectClass: organizationalUnit\n" +
            "objectClass: top\n" +
            "ou: people\n\n" +
            "dn: uid=jdoe,ou=people,dc=iam,dc=local\n" +
            "objectClass: inetOrgPerson\n" +
            "objectClass: top\n" +
            "uid: jdoe\n" +
            "cn: John Doe\n" +
            "sn: Doe\n" +
            "mail: jdoe@iam.local\n" +
            "userPassword: secret123\n";
        LDIFReader r = new LDIFReader(new ByteArrayInputStream(ldif.getBytes()));
        Entry e;
        while ((e = r.readEntry()) != null) ds.add(e);
    }

    @AfterAll
    static void stopLdap() { if (ds != null) ds.shutDown(true); }

    @BeforeEach
    void setup() {
        when(cache.tryAcquireLogin(any())).thenReturn(true);
        when(cache.isLocked(any())).thenReturn(false);
        // reconfigure LDAP pointing at in-memory server (test profile leaves url empty)
        ldapConfig.reconfigure("ldap://localhost:" + port, "dc=iam,dc=local",
                "uid={0},ou=people", "", "", "(uid={0})");
        seeder.run();
    }

    @Test
    void ldapBind_success_provisionsUser() {
        TokenResponse r = ldapAuth.authenticate("jdoe", "secret123", "default", "127.0.0.1");
        assertNotNull(r.getAccessToken());
    }

    @Test
    void ldapBind_wrongPassword_rejected() {
        assertThrows(Exception.class, () ->
            ldapAuth.authenticate("jdoe", "wrong", "default", "127.0.0.1"));
    }
}
