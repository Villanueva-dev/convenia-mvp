package com.uniremington.api.convenia.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central Spring Security configuration.
 *
 * <p>
 * Key decisions:
 * </p>
 * <ul>
 *   <li><strong>STATELESS</strong>: no HTTP session. Authentication state lives
 *       only in the JWT, validated on every request by {@link JwtAuthenticationFilter}.</li>
 *   <li><strong>CSRF disabled</strong>: safe for stateless REST APIs because
 *       there is no session cookie to forge.</li>
 *   <li><strong>{@code @EnableMethodSecurity}</strong>: enables {@code @PreAuthorize}
 *       annotations on service and controller methods for fine-grained role control.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Defines the security filter chain applied to all HTTP requests.
     *
     * <p>Public routes (no token needed):</p>
     * <ul>
     *   <li>{@code /auth/**} — login endpoint</li>
     *   <li>{@code /v3/api-docs/**}, {@code /swagger-ui/**} — Swagger UI</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} builder provided by Spring
     * @return the configured {@link SecurityFilterChain}
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── Disable CSRF — safe for stateless JWT APIs ───────────────
                .csrf(AbstractHttpConfigurer::disable)

                // ── CORS — allow configured origins ──────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── Session — never create an HTTP session ───────────────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Route authorization ──────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())

                // ── JWT filter runs before Spring's default password filter ──
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder bean.
     *
     * <p>
     * Spring Security uses this to hash passwords on registration and to
     * compare hashes during login. BCrypt strength defaults to 10 rounds.
     * </p>
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean so it can be
     * injected into {@code AuthServiceImpl} to trigger the login flow.
     *
     * @param config Spring's auto-configured {@link AuthenticationConfiguration}
     * @return the application's {@link AuthenticationManager}
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configures CORS: which frontend origins, methods, and headers are allowed.
     * Origins are read from {@code app.cors.allowed-origins} in {@code application.yml}.
     *
     * @return the CORS configuration source applied to all routes
     */
    private CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-University-ID"));
        config.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
