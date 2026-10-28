package br.com.vinhariaagnello.wineguard.repository;

import br.com.vinhariaagnello.wineguard.model.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
}
