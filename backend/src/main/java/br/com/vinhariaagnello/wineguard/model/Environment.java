package br.com.vinhariaagnello.wineguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "environments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** Ambiente lógico ao qual a equipe associa os dispositivos físicos. */
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    @Setter
    private String name;

    @Size(max = 500)
    @Column(length = 500)
    @Setter
    private String description;

    /** Cria ambiente com nome e descrição informados pelo painel. */
    public Environment(String name, String description) {
        this.name = name;
        this.description = description;
    }

}
