package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;

import br.com.vinhariaagnello.wineguard.model.Environment;
import br.com.vinhariaagnello.wineguard.dto.EnvironmentDtos;
import br.com.vinhariaagnello.wineguard.repository.EnvironmentRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class EnvironmentService {
    private final EnvironmentRepository repository;

    @Transactional(readOnly = true)
    /** Lista os ambientes de armazenamento cadastrados. */
    public List<EnvironmentDtos.Response> findAll() {
        return repository.findAll().stream().map(EnvironmentDtos.Response::from).toList();
    }

    /** Cadastra ambiente com nome único, sem diferenciar maiúsculas e minúsculas. */
    public EnvironmentDtos.Response create(EnvironmentDtos.CreateRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um ambiente com esse nome.");
        }
        return EnvironmentDtos.Response.from(repository.save(new Environment(request.name(), request.description())));
    }

    /** Busca ambiente necessário para associação com um dispositivo. */
    public Environment require(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ambiente não encontrado."));
    }
}
