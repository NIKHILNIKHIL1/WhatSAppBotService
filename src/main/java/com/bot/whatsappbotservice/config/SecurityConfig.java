package com.bot.whatsappbotservice.config;

import com.bot.whatsappbotservice.security.JwtAuthenticationFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.security.TenantContextSessionFilter;
import com.bot.whatsappbotservice.storefront.StorefrontTenantContextFilter;
import com.bot.whatsappbotservice.tenant.TenantRepository;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Three independent filter chains: {@code /ui/**} (session cookie, form login, CSRF enabled — a
 * browser can't easily attach a Bearer token to a plain navigation) for vendor {@code TenantUser}
 * accounts, {@code /store/**} (session cookie, CSRF enabled, but a customer-specific OTP login
 * instead of form login — see {@code storefront.StorefrontAuthController}) for {@code Customer}
 * storefront visitors, and everything else (stateless JWT, CSRF disabled) for the vendor REST API.
 * Order matters: Spring Security tries chains in ascending order and the first whose
 * {@code securityMatcher} matches wins the whole request, so the two narrower matchers must come
 * before the catch-all — each chain has a distinct {@code @Order} so tie-breaking is never
 * ambiguous.
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
                        .requestMatchers("/ui/register", "/ui/forgot-password", "/ui/reset-password",
                                "/ui/css/**").permitAll()
                        // Belt to the @PreAuthorize braces on AdminTenantUiController/TenantService.
                        .requestMatchers("/ui/admin/**").hasRole("SUPER_ADMIN")
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
    public SecurityFilterChain storefrontSecurityFilterChain(HttpSecurity http, TenantRepository tenantRepository,
                                                               RateLimitingFilter rateLimitingFilter,
                                                               SecurityContextRepository storefrontSecurityContextRepository)
            throws Exception {
        http
                .securityMatcher("/store/**")
                .securityContext(sc -> sc.securityContextRepository(storefrontSecurityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/store/{slug}", "/store/{slug}/login", "/store/{slug}/verify",
                                "/store/{slug}/products/**").permitAll()
                        .anyRequest().authenticated())
                // No formLogin() here (OTP login isn't Spring's username/password flow), so without
                // this an unauthenticated hit on a protected page (cart/checkout/orders) would 403
                // instead of sending the customer to log in — redirect to that tenant's own login
                // page, parsed from the URL the same way StorefrontTenantContextFilter does.
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
                    String[] segments = request.getRequestURI().split("/", 4);
                    String slug = segments.length >= 3 ? segments[2] : "";
                    response.sendRedirect(request.getContextPath() + "/store/" + slug + "/login");
                }))
                .addFilterBefore(new StorefrontTenantContextFilter(tenantRepository), SecurityContextHolderFilter.class)
                .addFilterBefore(rateLimitingFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    public SecurityContextRepository storefrontSecurityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(3)
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
