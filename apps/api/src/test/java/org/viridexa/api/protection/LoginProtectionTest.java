package org.viridexa.api.protection;
import org.junit.jupiter.api.Test;import java.time.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class LoginProtectionTest {
 static class Time extends Clock {long millis=1000;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochMilli(millis);}}
 LoginProtection service(TurnstileVerifier v,Time time){var p=new TurnstileProperties();p.setEnabled(true);return new LoginProtection(v,p,time);}
 @Test void normalLoginDoesNotChallengeButRepeatedFailureDoes(){var v=mock(TurnstileVerifier.class);var p=service(v,new Time());p.beforeLogin("a","ip",null);verifyNoInteractions(v);for(int i=0;i<3;i++)p.failed("a","ip");p.beforeLogin("a","other-ip",null);verify(v).verify(null,"login");p.succeeded("a");reset(v);p.beforeLogin("a","other-ip",null);verifyNoInteractions(v);}
 @Test void ipRiskSpansDifferentAccountsButNotDifferentIps(){var v=mock(TurnstileVerifier.class);var p=service(v,new Time());for(int i=0;i<10;i++)p.failed("a"+i,"ip");p.beforeLogin("new","other",null);verifyNoInteractions(v);p.beforeLogin("new","ip","fresh");verify(v).verify("fresh","login");}
 @Test void finiteLockoutExpiresWithoutManualUnlock(){var time=new Time();var v=mock(TurnstileVerifier.class);var p=service(v,time);for(int i=0;i<12;i++)p.failed("a","ip");assertEquals(429,assertThrows(ProtectionException.class,()->p.beforeLogin("a","newip",null)).status);time.millis+=600001;assertDoesNotThrow(()->p.beforeLogin("a","ip",null));verifyNoInteractions(v);}
}
