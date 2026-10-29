package br.com.vinhariaagnello.wineguard.controller;

import br.com.vinhariaagnello.wineguard.config.SecurityConfiguration;
import br.com.vinhariaagnello.wineguard.dto.AppUserDtos;
import br.com.vinhariaagnello.wineguard.enums.AppUserRole;
import br.com.vinhariaagnello.wineguard.service.AppUserAdministrationService;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AdminUserSecurityTest.WebConfig.class)
class AdminUserSecurityTest {

    private static final String CREATE_BODY = """
            {
              "username": "funcionario",
              "password": "Senha-ficticia-123!",
              "location": {
                "wineryName": "Vinícola de teste",
                "city": "São Paulo",
                "region": "São Paulo",
                "country": "Brasil",
                "latitude": -23.550520,
                "longitude": -46.633308
              }
            }
            """;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            SecurityConfiguration.class,
            AdminUserController.class,
            AuthController.class,
            WineryMapController.class
    })
    static class WebConfig {

        @Bean
        AppUserAdministrationService administrationService() {
            return mock(AppUserAdministrationService.class);
        }

        @Bean
        UserDetailsService authenticationUsers() {
            return new InMemoryUserDetailsManager();
        }
        @Bean
        br.com.vinhariaagnello.wineguard.repository.AppUserRepository mapUsers() {
            return mock(
                    br.com.vinhariaagnello.wineguard.repository.AppUserRepository.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserAdministrationService users;

    @Autowired
    private br.com.vinhariaagnello.wineguard.repository.AppUserRepository mapUsers;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(users, mapUsers);

        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousCannotListUsers() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(users);
    }

    @Test
    void ordinaryUserCannotListUsers() throws Exception {
        mvc.perform(get("/api/admin/users")
                        .with(user("funcionario").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users);
    }

    @Test
    void adminCanListWithoutExposingPassword() throws Exception {
        when(users.findAll()).thenReturn(List.of(
                new AppUserDtos.Response(
                        2L, "funcionario", AppUserRole.USER, true, null)));

        mvc.perform(get("/api/admin/users")
                        .with(user("dono").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username")
                        .value("funcionario"))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void adminCannotCreateWithoutCsrf() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .with(user("dono").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users);
    }

    @Test
    void ordinaryUserCannotCreateEvenWithCsrf() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .with(user("funcionario").roles("USER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users);
    }

    @Test
    void anonymousCannotCreateEvenWithCsrf() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(users);
    }

    @Test
    void adminCanCreateWithCsrf() throws Exception {
        when(users.create(any(AppUserDtos.CreateRequest.class)))
                .thenReturn(new AppUserDtos.Response(
                        2L, "funcionario", AppUserRole.USER, true, null));

        mvc.perform(post("/api/admin/users")
                        .with(user("dono").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(users, times(1))
                .create(any(AppUserDtos.CreateRequest.class));
    }

    @Test
    void invalidPasswordIsRejectedBeforeCallingService()
            throws Exception {

        mvc.perform(post("/api/admin/users")
                        .with(user("dono").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace(
                                "Senha-ficticia-123!", "curta")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(users);
    }

    @Test
    void sessionResponseReportsItsRole() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .with(user("dono").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(get("/api/auth/me")
                        .with(user("funcionario").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void missingLocationIsRejectedBeforeCallingService()
            throws Exception {
        mvc.perform(post("/api/admin/users")
                        .with(user("dono").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "funcionario",
                                  "password": "Senha-ficticia-123!"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(users);
    }

    @Test
    void invalidCoordinatesAreRejectedBeforeCallingService()
            throws Exception {
        String[] invalidBodies = {
                CREATE_BODY.replace("-23.550520", "91"),
                CREATE_BODY.replace("-46.633308", "-181")
        };

        for (String body : invalidBodies) {
            mvc.perform(post("/api/admin/users")
                            .with(user("dono").roles("ADMIN"))
                            .with(csrf().asHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(users);
    }

    @Test
    void anonymousCannotReadWineryMap() throws Exception {
        mvc.perform(get("/api/wineries"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mapUsers);
    }

    @Test
    void bothRolesCanReadLocationsWithoutAccountOrSensorData()
            throws Exception {
        var point =
                new br.com.vinhariaagnello.wineguard.dto.WineryMapPoint(
                        2L,
                        "Vinícola de teste",
                        "São Paulo",
                        "São Paulo",
                        "Brasil",
                        new java.math.BigDecimal("-23.550520"),
                        new java.math.BigDecimal("-46.633308"));

        when(mapUsers.findWineryMapPoints())
                .thenReturn(List.of(point));

        for (String role : List.of("USER", "ADMIN")) {
            mvc.perform(get("/api/wineries")
                            .with(user("visitante").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            "Cache-Control", "no-store"))
                    .andExpect(jsonPath("$[0].wineryName")
                            .value("Vinícola de teste"))
                    .andExpect(jsonPath("$[0].latitude")
                            .value(-23.550520))
                    .andExpect(jsonPath("$[0].username").doesNotExist())
                    .andExpect(jsonPath("$[0].password").doesNotExist())
                    .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                    .andExpect(jsonPath("$[0].devices").doesNotExist())
                    .andExpect(jsonPath("$[0].temperature").doesNotExist());
        }

        verify(mapUsers, times(2)).findWineryMapPoints();
    }

}

