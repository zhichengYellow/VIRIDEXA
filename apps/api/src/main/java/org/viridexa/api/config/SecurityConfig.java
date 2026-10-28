package org.viridexa.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint((request,response,error) -> {response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"code\":\"UNAUTHORIZED\"}");}).accessDeniedHandler((request,response,error) -> {response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"code\":\"FORBIDDEN\"}");}))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/showcase", "/api/v1/security/meta", "/api/v1/csrf", "/api/v1/agent/meta").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/agent/runs", "/api/v1/agent/runs/*/cancel").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/agent/runs/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/session", "/api/v1/account/login", "/api/v1/account/register").permitAll()
                        .requestMatchers("/api/v1/account/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/workbench/compare").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/workbench/overview", "/api/v1/workbench/plants", "/api/v1/workbench/plants/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/workbench/samples", "/api/v1/workbench/points", "/api/v1/workbench/points/*/evidence").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/plants/**", "/api/v1/science/**", "/api/v1/graph/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api-docs/**", "/swagger-ui/**", "/docs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {return config.getAuthenticationManager();}

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${viridexa.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Accept", "Content-Type", "Authorization", "X-CSRF-TOKEN"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
