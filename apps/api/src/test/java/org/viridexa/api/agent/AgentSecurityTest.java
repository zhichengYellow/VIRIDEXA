package org.viridexa.api.agent;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viridexa.api.config.SecurityConfig;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@WebMvcTest(AgentController.class) @Import(SecurityConfig.class)
class AgentSecurityTest {
 @Autowired MockMvc mvc;@MockitoBean AgentService service;
 @Test void publicMetaIsReadableButMutationStillRequiresCsrf()throws Exception{given(service.meta()).willReturn(Map.of("modelConfigured",false));mvc.perform(get("/api/v1/agent/meta")).andExpect(status().isOk());mvc.perform(post("/api/v1/agent/runs").contentType("application/json").content("{\"question\":\"来源\"}")).andExpect(status().isForbidden());}
 @Test void anonymousRunWithCsrfStillRequiresLogin()throws Exception{mvc.perform(post("/api/v1/agent/runs").with(csrf()).contentType("application/json").content("{\"question\":\"来源\"}")).andExpect(status().isUnauthorized());}
 @Test void authenticatedMemberCanRunButCannotInventWriteTools()throws Exception{mvc.perform(post("/api/v1/agent/runs").with(user("member").roles("MEMBER")).with(csrf()).contentType("application/json").content("{\"question\":\"来源\"}")).andExpect(status().isAccepted());mvc.perform(post("/api/v1/agent/approve").with(user("member").roles("MEMBER")).with(csrf())).andExpect(status().isForbidden());}
 @Test void longQuestionsRejectedAndCancelRequiresCsrf()throws Exception{mvc.perform(post("/api/v1/agent/runs").with(user("member").roles("MEMBER")).with(csrf()).contentType("application/json").content("{\"question\":\""+"x".repeat(1501)+"\"}")).andExpect(status().isBadRequest());mvc.perform(post("/api/v1/agent/runs/anything/cancel")).andExpect(status().isForbidden());}
 @Test void modelCannotReachUnregisteredWriteOrAdminRoutes()throws Exception{mvc.perform(post("/api/v1/agent/approve").with(csrf())).andExpect(status().isUnauthorized());mvc.perform(post("/api/v1/admin/science/candidates/id/decisions").with(csrf())).andExpect(status().isUnauthorized());}
}
