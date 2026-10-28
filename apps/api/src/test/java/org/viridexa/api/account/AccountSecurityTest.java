package org.viridexa.api.account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.AuthenticationManager;
import org.viridexa.api.config.SecurityConfig;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AccountController.class) @Import(SecurityConfig.class)
class AccountSecurityTest {
 @MockitoBean org.viridexa.api.protection.LoginProtection protection;
 @Autowired MockMvc mvc;@MockitoBean AccountService service;@MockitoBean AccountRateLimiter limiter;@MockitoBean AuthenticationManager manager;
 @Test void registrationCannotBypassCsrf()throws Exception{mvc.perform(post("/api/v1/account/register").contentType("application/json").content("{\"email\":\"reader@example.invalid\",\"displayName\":\"Reader\",\"password\":\"long-private-secret\"}")).andExpect(status().isForbidden());then(service).shouldHaveNoInteractions();}
 @Test void ownSessionRequiresAuthentication()throws Exception{mvc.perform(get("/api/v1/account/session")).andExpect(status().isUnauthorized());}
 @Test void invalidEmailCannotReachRegistration()throws Exception{mvc.perform(post("/api/v1/account/register").with(csrf()).contentType("application/json").content("{\"email\":\"invalid\",\"displayName\":\"Reader\",\"password\":\"long-private-secret\"}")).andExpect(status().isBadRequest());then(service).shouldHaveNoInteractions();}
 @Test void membersCannotReachAdminRoutes()throws Exception{mvc.perform(get("/api/v1/admin/session").with(user("member").roles("MEMBER"))).andExpect(status().isForbidden());}
}
