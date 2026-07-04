package com.iam.adapter.controller;

import com.iam.app.service.AdminAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AdminAppService admin;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void deleteTenantRoutesDeleteRequestsToAdminService() throws Exception {
        mvc.perform(delete("/admin/api/tenants/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已删除"));

        verify(admin).deleteTenant("acme");
    }

    @Test
    void deleteTenantWithoutCodeReturnsBadRequest() throws Exception {
        mvc.perform(delete("/admin/api/tenants/"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("租户编码不能为空"));
    }
}
