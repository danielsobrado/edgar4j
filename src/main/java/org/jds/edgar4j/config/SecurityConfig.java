package org.jds.edgar4j.config;

import org.jds.edgar4j.properties.SecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    public SecurityConfig(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        ServerHttpSecurity configured = http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults());

        if (!securityProperties.isEnabled()) {
            return configured
                    .authorizeExchange(auth -> auth.anyExchange().permitAll())
                    .build();
        }

        return configured
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/**", "/actuator/**").authenticated()
                        .anyExchange().permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "edgar4j.security", name = "enabled", havingValue = "true")
    public ReactiveUserDetailsService userDetailsService() {
        String username = securityProperties.getUsername();
        String password = securityProperties.getPassword();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("edgar4j.security.username and edgar4j.security.password must be set when edgar4j.security.enabled=true");
        }

        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new MapReactiveUserDetailsService(
                User.withUsername(username)
                        .password(encoder.encode(password))
                        .roles("ADMIN")
                        .build()
        );
    }
}
