package br.com.vinhariaagnello.wineguard.repository;

import br.com.vinhariaagnello.wineguard.model.Environment;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso e consultas de existência para ambientes. */
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
    /** Compara o nome sem diferenciar letras maiúsculas de minúsculas. */
    boolean existsByNameIgnoreCase(String name);
}
