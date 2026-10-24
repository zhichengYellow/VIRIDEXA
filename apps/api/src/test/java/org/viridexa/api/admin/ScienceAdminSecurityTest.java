package org.viridexa.api.admin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viridexa.api.config.SecurityConfig;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScienceAdminController.class)
@Import(SecurityConfig.class)
class ScienceAdminSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean JdbcTemplate jdbc;
 @Test void anonymousNeedsAuthentication() throws Exception { mvc.perform(get("/api/v1/admin/science/quality")).andExpect(status().isUnauthorized()); }
 @Test void researcherCannotInspectCandidates() throws Exception { mvc.perform(get("/api/v1/admin/science/candidates").with(user("test").roles("RESEARCHER"))).andExpect(status().isForbidden()); }
 @Test void administratorCanInspectQuality() throws Exception { mvc.perform(get("/api/v1/admin/science/quality").with(user("test").roles("ADMIN"))).andExpect(status().isOk()); }
}
