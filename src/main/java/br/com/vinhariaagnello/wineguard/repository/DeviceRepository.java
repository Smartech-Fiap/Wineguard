package br.com.vinhariaagnello.wineguard.repository;

import br.com.vinhariaagnello.wineguard.model.Device;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas de persistência dos dispositivos. */
public interface DeviceRepository extends JpaRepository<Device, Long> {

    boolean existsByFiwareServiceAndFiwareServicePathAndFiwareId(
            String service,
            String servicePath,
            String fiwareId);

    boolean existsByFiwareServiceAndFiwareServicePathAndMqttDeviceId(
            String service,
            String servicePath,
            String mqttDeviceId);

    /** Serializa a criação de configurações para o mesmo dispositivo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :deviceId")
    Optional<Device> findForConfiguration(
            @Param("deviceId") Long deviceId);

    /** Lista somente os sensores cadastrados pelo proprietário. */
    List<Device> findAllByOwner_IdOrderByIdAsc(Long ownerId);

}
