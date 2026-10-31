package org.viridexa.api.account;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AccountRolesTest {
 @Test void defaultModeDoesNotDependOnPortableEnrollment(){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),eq(String.class),eq(7L))).thenReturn(List.of("ADMIN"));assertEquals(List.of("ADMIN"),AccountRoles.read(jdbc,7L,false));verify(jdbc).queryForList(argThat(sql->!sql.contains("public_member_account")),eq(String.class),eq(7L));}
 @Test void portableEnrollmentCanOnlyProjectTheFixedMemberRole(){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),eq(String.class),eq(9L),eq(9L))).thenReturn(List.of("MEMBER"));assertEquals(List.of("MEMBER"),AccountRoles.read(jdbc,9L,true));verify(jdbc).queryForList(argThat(sql->sql.contains("r.role_key='MEMBER'")&&sql.contains("UNION")&&!sql.contains("INSERT")),eq(String.class),eq(9L),eq(9L));}
}
