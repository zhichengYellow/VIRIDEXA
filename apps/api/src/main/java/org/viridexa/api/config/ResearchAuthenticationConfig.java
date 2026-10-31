package org.viridexa.api.config;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.viridexa.api.account.AccountRoles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ResearchAuthenticationConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService researchUsers(JdbcTemplate jdbc,@Value("${viridexa.accounts.registration-mode:procedure}") String registrationMode) {
        return email -> {
            var users=jdbc.query("SELECT id,email,password_hash,enabled FROM app_user WHERE email=?", (r,n) ->
                new Object[]{r.getLong("id"),r.getString("email"),r.getString("password_hash"),r.getBoolean("enabled")}, email);
            if (users.isEmpty()) throw new UsernameNotFoundException("Unknown user");
            var user=users.getFirst();
            var roles=AccountRoles.read(jdbc,user[0],registrationMode.equals("portable"));
            return User.withUsername((String)user[1]).password((String)user[2]).disabled(!(Boolean)user[3])
                .authorities(roles.stream().map(r -> "ROLE_"+r).toArray(String[]::new)).build();
        };
    }
}
