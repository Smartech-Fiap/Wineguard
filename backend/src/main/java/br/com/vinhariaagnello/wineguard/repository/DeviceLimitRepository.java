package br.com.vinhariaagnello.wineguard.repository;

import br.com.vinhariaagnello.wineguard.model.DeviceLimit;
import br.com.vinhariaagnello.wineguard.enums.Metric;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceLimitRepository extends JpaRepository<DeviceLimit, Long> {
    // O underscore/underline indica ao Spring Data que a consulta percorre device.id na entidade relacionada.
    List<DeviceLimit> findAllByDevice_IdOrderByMetric(Long deviceId);

    // Mantém a unicidade por dispositivo e métrica ao localizar um limite existente.
    Optional<DeviceLimit> findByDevice_IdAndMetric(Long deviceId, Metric metric);
}
