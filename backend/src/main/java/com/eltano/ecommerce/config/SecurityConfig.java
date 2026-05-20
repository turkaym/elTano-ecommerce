package com.eltano.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.enabled:true}")
    private boolean adminEnabled;

    @Value("${app.uploads.product-images.public-path:/uploads/product-images}")
    private String productImagesPublicPath;

    @Value("${app.cors.admin-allowed-origins:http://localhost:5173}")
    private String adminAllowedOrigins;

    @Value("${app.cors.storefront-allowed-origins:http://localhost:5173}")
    private String storefrontAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .requireCsrfProtectionMatcher(adminWriteRequestMatcher()))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/health", "/api/catalog/**")
                        .permitAll()
                        .requestMatchers(normalizedProductImagesPublicMatcher())
                        .permitAll()
                        .requestMatchers("/api/orders/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/mercadopago/webhook")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .access((authentication, context) -> {
                            if (!adminEnabled) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }

                            return new org.springframework.security.authorization.AuthorizationDecision(
                                    authentication.get().getAuthorities().stream()
                                            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
                        })
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration adminConfig = new CorsConfiguration();
        adminConfig.setAllowedOrigins(parseAllowedOrigins(adminAllowedOrigins));
        adminConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        adminConfig.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN"));
        adminConfig.setAllowCredentials(true);

        CorsConfiguration storefrontConfig = new CorsConfiguration();
        storefrontConfig.setAllowedOrigins(parseAllowedOrigins(storefrontAllowedOrigins));
        storefrontConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        storefrontConfig.setAllowedHeaders(List.of("*"));
        storefrontConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", adminConfig);
        source.registerCorsConfiguration("/**", storefrontConfig);
        return source;
    }

    private static List<String> parseAllowedOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            return List.of("http://localhost:5173");
        }

        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.admin-user:}") String adminUser,
            @Value("${app.security.admin-password:}") String adminPassword,
            @Value("${app.security.storefront-user:}") String storefrontUserName,
            @Value("${app.security.storefront-password:}") String storefrontPassword,
            @Value("${app.security.expired-admin-user:}") String expiredAdminUser,
            @Value("${app.security.expired-admin-password:}") String expiredAdminPassword) {
        UserDetails admin = User.withUsername(requireConfiguredSecret(adminUser, "ADMIN_BASIC_USER"))
                .password("{noop}" + requireConfiguredSecret(adminPassword, "ADMIN_BASIC_PASS"))
                .roles("ADMIN")
                .build();

        UserDetails storefrontUser = User.withUsername(requireConfiguredSecret(storefrontUserName, "STOREFRONT_BASIC_USER"))
                .password("{noop}" + requireConfiguredSecret(storefrontPassword, "STOREFRONT_BASIC_PASS"))
                .roles("USER")
                .build();

        UserDetails expiredAdmin = User.withUsername(requireConfiguredSecret(expiredAdminUser, "EXPIRED_ADMIN_BASIC_USER"))
                .password("{noop}" + requireConfiguredSecret(expiredAdminPassword, "EXPIRED_ADMIN_BASIC_PASS"))
                .roles("ADMIN")
                .accountExpired(true)
                .build();

        return new InMemoryUserDetailsManager(admin, storefrontUser, expiredAdmin);
    }

    private static String requireConfiguredSecret(String value, String envName) {
        if (value == null || value.isBlank()) {
            if (isTestRuntime()) {
                return testOnlyCredential(envName);
            }

            throw new IllegalStateException(envName + " must be configured");
        }

        return value;
    }

    private static boolean isTestRuntime() {
        try {
            Class.forName("org.springframework.boot.test.context.SpringBootTest");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static String testOnlyCredential(String envName) {
        return switch (envName) {
            case "ADMIN_BASIC_USER" -> "admin-user";
            case "ADMIN_BASIC_PASS" -> "admin-pass";
            case "STOREFRONT_BASIC_USER" -> "storefront-user";
            case "STOREFRONT_BASIC_PASS" -> "storefront-pass";
            case "EXPIRED_ADMIN_BASIC_USER" -> "expired-admin";
            case "EXPIRED_ADMIN_BASIC_PASS" -> "expired-pass";
            default -> throw new IllegalStateException(envName + " must be configured");
        };
    }

    private RequestMatcher adminWriteRequestMatcher() {
        return request -> {
            String path = request.getRequestURI();
            if (path == null || !path.startsWith("/api/admin/")) {
                return false;
            }

            String method = request.getMethod();
            return !HttpMethod.GET.matches(method)
                    && !HttpMethod.HEAD.matches(method)
                    && !HttpMethod.OPTIONS.matches(method)
                    && !HttpMethod.TRACE.matches(method);
        };
    }

    private String normalizedProductImagesPublicMatcher() {
        String path = productImagesPublicPath == null || productImagesPublicPath.isBlank()
                ? "/uploads/product-images"
                : productImagesPublicPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path + "/**";
    }

}
