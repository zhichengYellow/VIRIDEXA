package org.viridexa.api.graph;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viridexa.api.config.SecurityConfig;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.viridexa.api.graph.GraphContract.*;
import java.util.*;

@WebMvcTest({GraphController.class, AdminGraphController.class})
@Import(SecurityConfig.class)
class GraphSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean GraphService service;

 @Test void anonymousCanReadPublicMetadata() throws Exception {
  given(service.meta()).willReturn(new Meta("PUBLIC", List.of("SOURCE"), List.of("SOURCE_FILE"), Map.of("SOURCE",1L), Map.of(), 0, "notice"));
  mvc.perform(get("/api/v1/graph/meta")).andExpect(status().isOk());
 }
 @Test void adminGraphRequiresAuthentication() throws Exception {
  mvc.perform(get("/api/v1/admin/graph/meta")).andExpect(status().isUnauthorized());
 }
 @Test void researcherCannotReadAdminGraph() throws Exception {
  mvc.perform(get("/api/v1/admin/graph/meta").with(user("researcher").roles("RESEARCHER"))).andExpect(status().isForbidden());
 }
 @Test void adminCanReadAdminGraph() throws Exception {
  given(service.adminMeta()).willReturn(new Meta("ADMIN", List.of(), List.of(), Map.of(), Map.of(), 0, "notice"));
  mvc.perform(get("/api/v1/admin/graph/meta").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
 }
 @Test void graphDoesNotExposeMutationRoute() throws Exception {
  mvc.perform(post("/api/v1/graph/meta").with(csrf())).andExpect(status().isUnauthorized());
 }
}
