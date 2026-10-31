package org.viridexa.api.config;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TiDbIntegrityTest {
 JdbcTemplate jdbc(){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(String.class))).thenReturn("1");when(jdbc.queryForList("SHOW GRANTS",String.class)).thenReturn(List.of("GRANT USAGE ON *.* TO 'reader'@'%'","GRANT SELECT ON release.* TO 'reader'@'%"));return jdbc;}
 @Test void readonlyWithEnforcedIntegrityIsAccepted(){assertDoesNotThrow(()->new TiDbIntegrityConfiguration().tidbSafetyGate(jdbc()).afterSingletonsInstantiated());}
 @Test void disabledCheckConstraintsAreRejected(){var jdbc=jdbc();when(jdbc.queryForObject("SELECT @@tidb_enable_check_constraint",String.class)).thenReturn("0");assertThrows(IllegalStateException.class,()->new TiDbIntegrityConfiguration().tidbSafetyGate(jdbc).afterSingletonsInstantiated());}
 @Test void overprivilegedPublicIdentityIsRejected(){var jdbc=jdbc();when(jdbc.queryForList("SHOW GRANTS",String.class)).thenReturn(List.of("GRANT SELECT, INSERT, UPDATE ON release.* TO 'writer'@'%"));assertThrows(IllegalStateException.class,()->new TiDbIntegrityConfiguration().tidbSafetyGate(jdbc).afterSingletonsInstantiated());}
 @Test void selectDelegationIsRejected(){var jdbc=jdbc();when(jdbc.queryForList("SHOW GRANTS",String.class)).thenReturn(List.of("GRANT SELECT ON release.* TO 'reader'@'%' WITH GRANT OPTION"));assertThrows(IllegalStateException.class,()->new TiDbIntegrityConfiguration().tidbSafetyGate(jdbc).afterSingletonsInstantiated());}
 @Test void inheritedRolesAreNotAssumedReadonly(){var jdbc=jdbc();when(jdbc.queryForList("SHOW GRANTS",String.class)).thenReturn(List.of("GRANT 'operator'@'%' TO 'reader'@'%"));assertThrows(IllegalStateException.class,()->new TiDbIntegrityConfiguration().tidbSafetyGate(jdbc).afterSingletonsInstantiated());}

}
