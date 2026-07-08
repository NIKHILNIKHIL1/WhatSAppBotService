package com.bot.whatsappbotservice.config;

import com.bot.whatsappbotservice.security.JwtAuthenticationFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.security.TenantContextSessionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Two independent filter chains: {@code /ui/**} (session cookie, form login, CSRF enabled — a
 * browser can't easily attach a Bearer token to a plain navigation) and everything else (stateless
 * JWT, CSRF disabled). Both share the same {@code AppUserDetailsService}/{@code PasswordEncoder}
 * beans, so one set of {@code TenantUser} accounts logs into either. Order matters: Spring Security
 * tries chains in ascending order and the first whose {@code securityMatcher} matches wins the
 * whole request, so the UI chain (narrower matcher) must come first.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_API_ENDPOINTS = {
            "/api/auth/**",
            "/api/whatsapp/webhook",
            "/api/twilio/webhook",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/ui/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ui/register", "/ui/css/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/ui/login")
                        .defaultSuccessUrl("/ui/dashboard", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/ui/logout")
                        .logoutSuccessUrl("/ui/login?logout")
                        .permitAll())
                .addFilterAfter(new TenantContextSessionFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtService jwtService,
                                                        RequestIdFilter requestIdFilter,
                                                        RateLimitingFilter rateLimitingFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_API_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(requestIdFilter, RateLimitingFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
