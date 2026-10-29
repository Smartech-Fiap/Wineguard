package br.com.vinhariaagnello.wineguard.controller;

import br.com.vinhariaagnello.wineguard.dto.WineryMapPoint;
import br.com.vinhariaagnello.wineguard.repository.AppUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wineries")
@RequiredArgsConstructor
public class WineryMapController {

    private final AppUserRepository users;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<WineryMapPoint>> findAll() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(users.findWineryMapPoints());
    }
}
