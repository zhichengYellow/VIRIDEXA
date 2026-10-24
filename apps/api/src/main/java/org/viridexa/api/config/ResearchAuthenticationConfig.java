package org.viridexa.api.config;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ResearchAuthenticationConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService researchUsers(JdbcTemplate jdbc) {
        return email -> {
            var users=jdbc.query("SELECT id,email,password_hash,enabled FROM app_user WHERE email=?", (r,n) ->
                new Object[]{r.getLong("id"),r.getString("email"),r.getString("password_hash"),r.getBoolean("enabled")}, email);
            if (users.isEmpty()) throw new UsernameNotFoundException("Unknown user");
            var user=users.getFirst();
            var roles=jdbc.queryForList("SELECT r.role_key FROM app_role r JOIN user_role ur ON ur.role_id=r.id WHERE ur.user_id=?",String.class,user[0]);
            return User.withUsername((String)user[1]).password((String)user[2]).disabled(!(Boolean)user[3])
                .authorities(roles.stream().map(r -> "ROLE_"+r).toArray(String[]::new)).build();
        };
    }
}
