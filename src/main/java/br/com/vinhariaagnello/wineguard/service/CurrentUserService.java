package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.model.AppUser;
import br.com.vinhariaagnello.wineguard.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository users;

    @Transactional(readOnly = true)
    public AppUser require() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new InsufficientAuthenticationException(
                    "Autenticação necessária.");
        }

        return users.findByUsername(authentication.getName())
                .filter(AppUser::isEnabled)
                .orElseThrow(() ->
                        new InsufficientAuthenticationException(
                                "A sessão não corresponde "
                                        + "a um usuário habilitado."));
    }
}
