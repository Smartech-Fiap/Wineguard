package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.dto.AppUserDtos;
import br.com.vinhariaagnello.wineguard.enums.AppUserRole;
import br.com.vinhariaagnello.wineguard.model.AppUser;
import br.com.vinhariaagnello.wineguard.repository.AppUserRepository;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppUserAdministrationServiceTest {

    private AppUserRepository users;
    private PasswordEncoder encoder;
    private AppUserAdministrationService service;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        encoder = PasswordEncoderFactories
                .createDelegatingPasswordEncoder();

        service = new AppUserAdministrationService(users, encoder);

        when(users.findByUsername("novo.usuario"))
                .thenReturn(Optional.empty());

        when(users.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AppUserDtos.CreateRequest request(
            String username, String password) {
        return new AppUserDtos.CreateRequest(
                username,
                password,
                new br.com.vinhariaagnello.wineguard.dto.WineryLocation(
                        "Vinícola de teste",
                        "São Paulo",
                        "São Paulo",
                        "Brasil",
                        new java.math.BigDecimal("-23.550520"),
                        new java.math.BigDecimal("-46.633308")));
    }

    @Test
    void storesHashAndCreatesOnlyUserRole() {
        String password = "Senha-ficticia-123!";
        var request = request(
                "novo.usuario", password);

        var response = service.create(request);

        var captured = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(captured.capture());

        AppUser saved = captured.getValue();

        assertNotEquals(password, saved.getPasswordHash());
        assertTrue(encoder.matches(password, saved.getPasswordHash()));
        assertEquals(AppUserRole.USER, saved.getRole());
        assertEquals(AppUserRole.USER, response.role());
        assertEquals("novo.usuario", response.username());
        assertTrue(response.enabled());
        assertEquals("Vinícola de teste", saved.getWineryName());
        assertEquals("São Paulo", saved.getCity());
        assertEquals("São Paulo", saved.getRegion());
        assertEquals("Brasil", saved.getCountry());
        assertEquals(request.location().latitude(), saved.getLatitude());
        assertEquals(request.location().longitude(), saved.getLongitude());
        assertEquals(request.location(), response.location());
    }

    @Test
    void rejectsDuplicateUsernameWithoutSaving() {
        when(users.findByUsername("novo.usuario"))
                .thenReturn(Optional.of(
                        new AppUser("novo.usuario", "hash-ficticio")));

        assertThrows(ConflictException.class,
                () -> service.create(request(
                        "novo.usuario", "Senha-ficticia-123!")));

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPasswordAbove72Utf8Bytes() {
        String password = "á".repeat(37);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request(
                        "novo.usuario", password)));

        verify(users, never()).findByUsername(anyString());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void acceptsPasswordAt72ByteBoundary() {
        String password = "A".repeat(72);

        service.create(request(
                "novo.usuario", password));

        var captured = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(captured.capture());

        assertTrue(encoder.matches(
                password, captured.getValue().getPasswordHash()));
    }

    @Test
    void listingReturnsOnlyPublicUserInformation() {
        when(users.findAll(Sort.by("username")))
                .thenReturn(List.of(
                        new AppUser("usuario", "hash-ficticio")));

        var response = service.findAll();

        assertEquals(1, response.size());
        assertEquals("usuario", response.get(0).username());
        assertEquals(AppUserRole.USER, response.get(0).role());
        assertFalse(response.toString().contains("hash-ficticio"));
    }

    @Test
    void requestToStringDoesNotExposePassword() {
        String password = "Senha-ficticia-123!";
        var request = request(
                "novo.usuario", password);

        assertFalse(request.toString().contains(password));
        assertTrue(request.toString().contains("<redacted>"));
    }

    @Test
    void rejectsMissingLocationWithoutSaving() {
        var request = new AppUserDtos.CreateRequest(
                "novo.usuario", "Senha-ficticia-123!", null);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request));

        verify(users, never()).saveAndFlush(any());
    }

}
