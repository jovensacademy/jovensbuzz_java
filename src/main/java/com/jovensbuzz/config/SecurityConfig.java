package com.jovensbuzz.config;

import com.jovensbuzz.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    @Value("${app.security.swagger-enabled:true}")
    private boolean swaggerEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF is not applicable: the API is stateless and authenticates only via the
            // Authorization header (never cookies), which browsers do not attach automatically.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(content -> {})
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .xssProtection(xss -> xss.disable()) // obsolete header; modern browsers rely on CSP
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeJsonError(response, 401, "Authentication required"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJsonError(response, 403, "You do not have permission to access this resource"))
            )
            .authorizeHttpRequests(auth -> {
                // Public Website APIs
                auth.requestMatchers(HttpMethod.POST, "/api/v1/enquiries").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/services/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/faqs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/site-content/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // Public Auth APIs (logout is idempotent; it revokes tokens when one is presented)
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()

                    // Actuator: only the redacted health/info endpoints are exposed
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/error").permitAll();

                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                }

                // Protected Admin APIs
                auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/auth/**").authenticated()
                    .anyRequest().denyAll();
            });

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
