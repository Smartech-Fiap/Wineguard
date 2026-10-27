package br.com.vinhariaagnello.wineguard.repository;

import br.com.vinhariaagnello.wineguard.model.Device;

import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de persistência para dispositivos registrados na aplicação. */
public interface DeviceRepository extends JpaRepository<Device, Long> {
    /** Evita cadastrar duas vezes o mesmo ID FIWARE dentro do mesmo contexto. */
    boolean existsByFiwareServiceAndFiwareServicePathAndFiwareId(String service, String servicePath, String fiwareId);
}
