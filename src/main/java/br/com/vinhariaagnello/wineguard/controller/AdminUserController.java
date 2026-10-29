package br.com.vinhariaagnello.wineguard.controller;

import br.com.vinhariaagnello.wineguard.dto.AppUserDtos;
import br.com.vinhariaagnello.wineguard.service.AppUserAdministrationService;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserAdministrationService users;

    @GetMapping
    public ResponseEntity<List<AppUserDtos.Response>> findAll() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(users.findAll());
    }

    @PostMapping
    public ResponseEntity<AppUserDtos.Response> create(
            @Valid @RequestBody AppUserDtos.CreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(users.create(request));
    }
}
