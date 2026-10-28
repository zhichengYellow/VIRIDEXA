package org.viridexa.api.workbench;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viridexa.api.config.SecurityConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.verifyNoInteractions;
@WebMvcTest(WorkbenchController.class) @Import(SecurityConfig.class)
class WorkbenchSecurityTest {
 @Autowired MockMvc mvc; @MockitoBean WorkbenchService service;
 @Test void guestsCannotReadSamplesPointsOrEvidence()throws Exception{
  for(String path:new String[]{"/samples","/points?sampleIds=sample:x","/points/candidate:x/evidence"})mvc.perform(get("/api/v1/workbench"+path)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/workbench/compare").with(csrf()).contentType("application/json").content("{\"pointIds\":[]}")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
 }
 @Test void membersCanExploreButCannotAccessAdminCandidates()throws Exception{
  mvc.perform(get("/api/v1/workbench/samples").with(user("member").roles("MEMBER"))).andExpect(status().isOk());
  mvc.perform(get("/api/v1/admin/workbench/samples").with(user("member").roles("MEMBER"))).andExpect(status().isForbidden());
 }
 @Test void catalogMetadataAndCoverageRemainPublic()throws Exception{
  mvc.perform(get("/api/v1/workbench/overview")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/workbench/plants")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/workbench/plants/1")).andExpect(status().isOk());
 }
}
