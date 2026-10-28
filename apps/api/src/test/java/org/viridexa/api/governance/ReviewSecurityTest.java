package org.viridexa.api.governance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.viridexa.api.config.SecurityConfig;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean ReviewService service;
 private final String url="/api/v1/admin/science/candidates/"+"a".repeat(64)+"/decisions";
 private final String body="{\"action\":\"APPROVED\",\"expectedVersion\":0,\"rationale\":\"Isolated technical test evidence only\",\"requestId\":\"00000000-0000-4000-8000-000000000001\"}";
 @Test void requiresAuthentication()throws Exception{mvc.perform(post(url).with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());}
 @Test void researcherCannotApprove()throws Exception{mvc.perform(post(url).with(csrf()).with(user("fixture").roles("RESEARCHER")).contentType("application/json").content(body)).andExpect(status().isForbidden());}
 @Test void csrfIsRequiredEvenForAdmin()throws Exception{mvc.perform(post(url).with(user("fixture").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isForbidden());}
 @Test void authorizedAdminCanSubmitValidatedCommand()throws Exception{given(service.decide(anyString(),any(),any())).willReturn(new ReviewService.Result("a".repeat(64),1,"APPROVED",1,false));mvc.perform(post(url).with(csrf()).with(user("fixture").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));}
 @Test void negativeVersionIsRejectedBeforeService()throws Exception{mvc.perform(post(url).with(csrf()).with(user("fixture").roles("ADMIN")).contentType("application/json").content(body.replace("\"expectedVersion\":0","\"expectedVersion\":-1"))).andExpect(status().isBadRequest());then(service).shouldHaveNoInteractions();}
 @Test void missingRationaleIsRejected()throws Exception{mvc.perform(post(url).with(csrf()).with(user("fixture").roles("ADMIN")).contentType("application/json").content(body.replace("Isolated technical test evidence only",""))).andExpect(status().isBadRequest());}
 @Test void failureDoesNotExposeDatabaseDetails()throws Exception{given(service.decide(anyString(),any(),any())).willThrow(new DataAccessResourceFailureException("secret server password path"));mvc.perform(post(url).with(csrf()).with(user("fixture").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret server"))));}
}
