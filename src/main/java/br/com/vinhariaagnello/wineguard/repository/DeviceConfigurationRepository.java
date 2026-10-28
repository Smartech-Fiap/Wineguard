package br.com.vinhariaagnello.wineguard.repository;

import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.model.DeviceConfiguration;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas das configurações enviadas e confirmadas pelo ESP. */
public interface DeviceConfigurationRepository
        extends JpaRepository<DeviceConfiguration, Long> {

    Optional<DeviceConfiguration> findFirstByDevice_IdOrderByRevisionDesc(
            Long deviceId);

    Optional<DeviceConfiguration> findByDevice_IdAndRevision(
            Long deviceId, Long revision);

    boolean existsByDevice_IdAndStatusIn(
            Long deviceId,
            Collection<ConfigurationStatus> statuses);

    /** Bloqueia o registro durante a atualização de seu estado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from DeviceConfiguration c
            where c.device.id = :deviceId
              and c.revision = :revision
            """)
    Optional<DeviceConfiguration> findForUpdate(
            @Param("deviceId") Long deviceId,
            @Param("revision") Long revision);
}
