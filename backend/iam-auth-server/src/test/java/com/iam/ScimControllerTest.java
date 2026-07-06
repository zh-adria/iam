package com.iam;

import com.iam.adapter.controller.ScimController;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.ScimGroupMemberRepository;
import com.iam.infrastructure.repository.ScimGroupRepository;
import com.iam.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ScimControllerTest {

    @Test
    void listUsers_returnsScimListResponse() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        ScimGroupRepository groupRepo = Mockito.mock(ScimGroupRepository.class);
        ScimController ctrl = new ScimController(repo, groupRepo, Mockito.mock(ScimGroupMemberRepository.class));
        UserEntity u = new UserEntity(); u.setId(1L); u.setUsername("alice");
        u.setPasswordHash("x"); u.setTenantCode("default"); u.setStatus(1);
        Mockito.when(repo.findAll()).thenReturn(List.of(u));

        MockHttpServletRequest req = new MockHttpServletRequest();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = ctrl.listUsers(1, 20, null, req);

        assertEquals(1, result.get("totalResults"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("Resources");
        assertEquals("alice", resources.get(0).get("userName"));
        @SuppressWarnings("unchecked")
        List<String> schemas = (List<String>) resources.get(0).get("schemas");
        assertThat(schemas.get(0), startsWith("urn:ietf:params:scim:schemas:core:2.0:User"));
    }

    @Test
    void getUser_returnsScimUser() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        ScimGroupRepository groupRepo = Mockito.mock(ScimGroupRepository.class);
        ScimController ctrl = new ScimController(repo, groupRepo, Mockito.mock(ScimGroupMemberRepository.class));
        UserEntity u = new UserEntity(); u.setId(5L); u.setUsername("bob");
        u.setPasswordHash("x"); u.setTenantCode("default"); u.setStatus(1); u.setEmail("bob@test");
        Mockito.when(repo.findById(5L)).thenReturn(Optional.of(u));

        var resp = ctrl.getUser(5L);
        Map<String, Object> body = resp.getBody();

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals("5", body.get("id"));
        assertEquals("bob", body.get("userName"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emails = (List<Map<String, Object>>) body.get("emails");
        assertEquals("bob@test", emails.get(0).get("value"));
    }

    @Test
    void getUser_notFound_returns404() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        ScimGroupRepository groupRepo = Mockito.mock(ScimGroupRepository.class);
        ScimController ctrl = new ScimController(repo, groupRepo, Mockito.mock(ScimGroupMemberRepository.class));
        Mockito.when(repo.findById(99L)).thenReturn(Optional.empty());

        var resp = ctrl.getUser(99L);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void createUser_returnsCreated() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        ScimGroupRepository groupRepo = Mockito.mock(ScimGroupRepository.class);
        ScimController ctrl = new ScimController(repo, groupRepo, Mockito.mock(ScimGroupMemberRepository.class));
        UserEntity saved = new UserEntity(); saved.setId(10L); saved.setUsername("charlie");
        saved.setPasswordHash("x"); saved.setTenantCode("default"); saved.setStatus(1);
        Mockito.when(repo.save(Mockito.any(UserEntity.class))).thenReturn(saved);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/scim/v2/Users");
        req.setServerName("localhost");
        req.setServerPort(8080);
        req.setScheme("http");

        Map<String, Object> body = new HashMap<>();
        body.put("userName", "charlie");
        body.put("emails", "c@test");
        var resp = ctrl.createUser(body, req);

        assertEquals(201, resp.getStatusCodeValue());
        assertNotNull(resp.getHeaders().getLocation());
        assertTrue(resp.getHeaders().getLocation().toString().contains("/scim/v2/Users/10"));
        assertEquals("charlie", resp.getBody().get("userName"));
    }

    @Test
    void deleteUser_returnsNoContent() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        ScimGroupRepository groupRepo = Mockito.mock(ScimGroupRepository.class);
        ScimController ctrl = new ScimController(repo, groupRepo, Mockito.mock(ScimGroupMemberRepository.class));
        Mockito.when(repo.existsById(1L)).thenReturn(true);

        var resp = ctrl.deleteUser(1L);
        assertEquals(204, resp.getStatusCodeValue());
    }
}
