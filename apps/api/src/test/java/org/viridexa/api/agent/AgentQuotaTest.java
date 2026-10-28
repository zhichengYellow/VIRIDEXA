package org.viridexa.api.agent;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class AgentQuotaTest {
 @TempDir Path dir;
 AgentProperties properties(){var p=new AgentProperties();p.setRuntimeDirectory(dir.toString());p.setUserDailyRequests(1);p.setGlobalDailyRequests(2);p.setUserDailyTokens(100);p.setGlobalDailyTokens(150);return p;}
 @Test void requestLimitsPersistAcrossInstancesAndDoNotStoreRawIdentity()throws Exception{var p=properties();new AgentQuota(p).start("private-name@example.invalid");assertThrows(AgentQuota.BudgetExceeded.class,()->new AgentQuota(p).start("private-name@example.invalid"));assertFalse(Files.readString(dir.resolve("quota.json")).contains("private-name"));}
 @Test void unknownUsageConsumesReservationAndActualUsageReleasesUnusedBudget(){var q=new AgentQuota(properties());q.reserve("a",80);q.settle("a",80,null);assertThrows(AgentQuota.BudgetExceeded.class,()->q.reserve("a",21));q.settle("a",80,40L);assertDoesNotThrow(()->q.reserve("a",60));}
 @Test void globalLimitCoversMultipleUsers(){var q=new AgentQuota(properties());q.start("a");q.start("b");assertThrows(AgentQuota.BudgetExceeded.class,()->q.start("c"));q.reserve("a",90);assertThrows(AgentQuota.BudgetExceeded.class,()->q.reserve("b",61));}
 @Test void monetaryBudgetRejectsCallsBeforeProviderSpendAndPersists(){var p=properties();p.setInputCostPerMillion(new java.math.BigDecimal("1"));p.setOutputCostPerMillion(new java.math.BigDecimal("10"));p.setUserDailyCost(new java.math.BigDecimal("0.0005"));var q=new AgentQuota(p);q.reserve("a",40);assertThrows(AgentQuota.BudgetExceeded.class,()->new AgentQuota(p).reserve("a",11));}
 @Test void monetaryBudgetCannotStartWithoutProviderPrices(){var p=properties();p.setGlobalDailyCost(java.math.BigDecimal.ONE);assertThrows(IllegalArgumentException.class,p::validate);}
 @Test void corruptBudgetFailsClosed()throws Exception{Files.writeString(dir.resolve("quota.json"),"not JSON");assertThrows(IllegalStateException.class,()->new AgentQuota(properties()));}
}
