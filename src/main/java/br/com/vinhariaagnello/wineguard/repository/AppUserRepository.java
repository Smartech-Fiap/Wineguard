package br.com.vinhariaagnello.wineguard.repository;

import br.com.vinhariaagnello.wineguard.model.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    @org.springframework.data.jpa.repository.Query("""
            select new br.com.vinhariaagnello.wineguard.dto.WineryMapPoint(
                u.id,
                u.wineryName,
                u.city,
                u.region,
                u.country,
                u.latitude,
                u.longitude
            )
            from AppUser u
            where u.enabled = true
              and u.wineryName is not null
              and u.latitude is not null
              and u.longitude is not null
            order by u.wineryName, u.id
            """)
    java.util.List<br.com.vinhariaagnello.wineguard.dto.WineryMapPoint>
            findWineryMapPoints();

}
