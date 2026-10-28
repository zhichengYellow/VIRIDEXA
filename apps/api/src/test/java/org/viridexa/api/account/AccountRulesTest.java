package org.viridexa.api.account;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class AccountRulesTest {
 @Test void passwordLengthUsesUtf8AndRejectsControls(){assertDoesNotThrow(()->AccountRules.password("植序研究"));assertThrows(ResponseStatusException.class,()->AccountRules.password("short"));assertThrows(ResponseStatusException.class,()->AccountRules.password("植".repeat(25)));assertThrows(ResponseStatusException.class,()->AccountRules.password("long-password\n"));}
 @Test void identityNormalizationDoesNotRequireARealName(){assertEquals("reader@example.com",AccountRules.email(" Reader@EXAMPLE.COM "));assertEquals("小树同学",AccountRules.name(" 小树同学 "));assertThrows(ResponseStatusException.class,()->AccountRules.name("\n\t"));}
 @Test void loginAbuseIsBoundedByIdentity(){var l=new AccountRateLimiter(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));for(int i=0;i<12;i++)l.check("login","local","reader@example.invalid");assertEquals(429,assertThrows(ResponseStatusException.class,()->l.check("login","other","reader@example.invalid")).getStatusCode().value());assertDoesNotThrow(()->l.check("login","local","different@example.invalid"));}
 @Test void requestDiagnosticsNeverContainCredentials(){assertFalse(new AccountController.Login("private@example.invalid","PRIVATE_SECRET",null).toString().contains("PRIVATE"));assertFalse(new AccountController.Registration("private@example.invalid","reader","PRIVATE_SECRET",null).toString().contains("private@example"));}
}
