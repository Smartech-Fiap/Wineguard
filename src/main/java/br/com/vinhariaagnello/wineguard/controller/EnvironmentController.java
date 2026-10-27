package br.com.vinhariaagnello.wineguard.controller;

import br.com.vinhariaagnello.wineguard.dto.EnvironmentDtos;
import br.com.vinhariaagnello.wineguard.service.EnvironmentService;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/environments")
@RequiredArgsConstructor
/** Endpoints para cadastrar e listar ambientes de armazenamento. */
public class EnvironmentController {
    private final EnvironmentService service;

    /** Lista os ambientes disponíveis para associar dispositivos. */
    @GetMapping
    public List<EnvironmentDtos.Response> findAll() { return service.findAll(); }

    /** Cria um ambiente e responde 201 com o endereço do novo recurso. */
    @PostMapping
    public ResponseEntity<EnvironmentDtos.Response> create(@Valid @RequestBody EnvironmentDtos.CreateRequest request) {
        EnvironmentDtos.Response created = service.create(request);
        return ResponseEntity.created(URI.create("/api/environments/" + created.id())).body(created);
    }
}
