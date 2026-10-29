package br.com.vinhariaagnello.wineguard.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,
                    "/api/auth/csrf", "/actuator/health").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(
                    "/api/admin", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .requestCache(cache -> cache.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(login -> login
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) ->
                    writeJson(response, 200,
                        "{\"status\":\"authenticated\"}"))
                .failureHandler((request, response, exception) ->
                    writeJson(response, 401,
                        "{\"error\":\"Credenciais inválidas.\"}"))
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) ->
                    writeJson(response, 200,
                        "{\"status\":\"logged_out\"}"))
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeJson(response, 401,
                        "{\"error\":\"Autenticação necessária.\"}"))
                .accessDeniedHandler((request, response, exception) ->
                    writeJson(response, 403,
                        "{\"error\":\"Acesso negado ou token CSRF inválido.\"}"))
            );

        return http.build();
    }

    private static void writeJson(
            HttpServletResponse response, int status, String body)
            throws IOException {

        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(body);
    }
}
