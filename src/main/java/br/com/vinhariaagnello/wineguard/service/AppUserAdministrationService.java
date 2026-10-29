package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.dto.AppUserDtos;
import br.com.vinhariaagnello.wineguard.model.AppUser;
import br.com.vinhariaagnello.wineguard.repository.AppUserRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppUserAdministrationService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<AppUserDtos.Response> findAll() {
        return users.findAll(Sort.by("username"))
                .stream()
                .map(AppUserDtos.Response::from)
                .toList();
    }

    @Transactional
    public AppUserDtos.Response create(
            AppUserDtos.CreateRequest request) {

        if (request.password()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "A senha deve ocupar no máximo 72 bytes em UTF-8.");
        }

        if (users.findByUsername(request.username()).isPresent()) {
            throw new ConflictException(
                    "O nome de usuário já está cadastrado.");
        }

        var location = request.location();
        if (location == null) {
            throw new IllegalArgumentException(
                    "Informe a localização da vinícola.");
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        AppUser user = new AppUser(
                request.username(),
                passwordHash);

        user.setWineryLocation(
                location.wineryName().trim(),
                location.city().trim(),
                location.region().trim(),
                location.country().trim(),
                location.latitude(),
                location.longitude());

        return AppUserDtos.Response.from(users.saveAndFlush(user));
    }
}
