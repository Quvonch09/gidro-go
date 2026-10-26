package uz.gidrogo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    /** Swagger va OpenAPI hujjatlari */
    @org.springframework.beans.factory.annotation.Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:https://gidrogo-miniapp.pages.dev,https://*.gidrogo-miniapp.pages.dev,https://web.telegram.org,https://*.telegram.org,https://169.58.215.170,http://169.58.215.170,https://169.58.215.170:8080,http://169.58.215.170:8080,http://localhost:*,https://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Avtorizatsiyadan o'tilmagan yoki token eskirgan (401 Unauthorized)\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"Ushbu amalni bajarish uchun ruxsatingiz yetarli emas (403 Forbidden)\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Allow all preflight OPTIONS requests for CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints: Asosiy sahifa, Swagger UI va OpenAPI hujjatlari
                        .requestMatchers("/", "/error", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/**", "/api/client/telegram/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/client/available-farms").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/client/farms", "/api/client/farms/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/client/banners", "/api/client/banners/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/view/**").permitAll()
                        .requestMatchers("/ws/**", "/api/telegram/**").permitAll()

                        // Role-based endpoints (RBAC matrix)
                        .requestMatchers("/api/superadmin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/boss/**").hasRole("BOSS")
                        .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "BOSS")
                        // BE-19 / CHK-08: har bir rol faqat o'z API'siga (GG-19, GG-25)
                        .requestMatchers("/api/courier/**").hasRole("COURIER")
                        .requestMatchers("/api/client/**").hasRole("CLIENT")
                        .requestMatchers("/api/files/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Requested-With", "X-Telegram-Bot-Api-Secret-Token", "*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Total-Count"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }
}
