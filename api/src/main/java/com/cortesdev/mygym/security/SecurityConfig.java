package com.cortesdev.mygym.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final List<String> allowedOrigins;
    private final DemoAccessExpiryFilter demoAccessExpiryFilter;

    public SecurityConfig(
            JwtService jwtService,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
            DemoAccessExpiryFilter demoAccessExpiryFilter) {
        this.jwtService = jwtService;
        this.allowedOrigins = allowedOrigins;
        this.demoAccessExpiryFilter = demoAccessExpiryFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/api/auth/**",
                                "/api/public/**",
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/gyms/**")
                        .hasRole("SUPER_ADMIN")
                        // DEMO_ADMIN es de solo-lectura: el matcher de GET tiene que ir ANTES del
                        // genérico de abajo (Spring Security usa el PRIMER matcher que hace match,
                        // mismo gotcha ya documentado para CORS más abajo en este archivo) — así,
                        // un DEMO_ADMIN que intente POST/PUT/DELETE cae al matcher genérico
                        // siguiente (solo GYM_ADMIN) y recibe 403 real, no solo un botón
                        // deshabilitado en el frontend.
                        .requestMatchers(HttpMethod.GET, "/api/gym-admin/**")
                        .hasAnyRole("GYM_ADMIN", "DEMO_ADMIN")
                        .requestMatchers("/api/gym-admin/**")
                        .hasRole("GYM_ADMIN")
                        .requestMatchers("/api/me/**")
                        .hasRole("MEMBER")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.decoder(jwtService.jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // ANTES de AuthorizationFilter: si el acceso demo ya venció, corta acá con 410
                // sin llegar siquiera a evaluar los matchers de arriba (ver DemoAccessExpiryFilter
                // — el JWT en sí sigue siendo válido por 30 días, este filtro es el único lugar
                // que de verdad hace cumplir el límite de 48h en una sesión ya logueada).
                .addFilterBefore(demoAccessExpiryFilter, AuthorizationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Flow/Webpay redirige el navegador del socio con un POST cross-origin de vuelta acá
        // (urlReturn) — nunca es nuestro propio frontend, así que el navegador manda un
        // Origin que jamás va a estar en la allowlist de abajo (pensada para www.mygym.cl).
        // TIENE que registrarse ANTES que "/**": UrlBasedCorsConfigurationSource devuelve la
        // config del PRIMER patrón registrado que matchea (no el más específico) — registrarlo
        // después de "/**" seguía cayendo en la config genérica y rechazando con
        // "Invalid CORS request" antes de que el controller llegara a hacer el redirect 302
        // hacia la SPA (confirmado con el patrón al revés, no alcanzaba con agregarlo nomás).
        CorsConfiguration flowConfig = new CorsConfiguration();
        flowConfig.setAllowedOriginPatterns(List.of("*"));
        flowConfig.setAllowedMethods(List.of("GET", "POST"));
        flowConfig.setAllowedHeaders(List.of("*"));
        source.registerCorsConfiguration("/api/public/flow/**", flowConfig);

        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("role");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
