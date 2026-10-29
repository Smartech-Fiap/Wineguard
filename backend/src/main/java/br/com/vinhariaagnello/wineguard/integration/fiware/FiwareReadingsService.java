package br.com.vinhariaagnello.wineguard.integration.fiware;

import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.enums.Metric;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
/** Mantém as chamadas de leitura para os serviços FIWARE existentes. */
public class FiwareReadingsService {
    private final RestClient orion;
    private final RestClient sth;

    public FiwareReadingsService(@Qualifier("orionRestClient") RestClient orion,
                                 @Qualifier("sthRestClient") RestClient sth) {
        this.orion = orion;
        this.sth = sth;
    }

    /** Lê o estado atual NGSIv2 diretamente do Orion. */
    public Map<String, Object> current(Device device) {
        return orion.get()
                .uri(uriBuilder -> uriBuilder.path("/v2/entities/{id}").queryParam("options", "keyValues")
                        .build(device.getFiwareId()))
                .header("Fiware-Service", device.getFiwareService())
                .header("Fiware-ServicePath", device.getFiwareServicePath())
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    /** Consulta no STH-Comet sem duplicar a série histórica no PostgreSQL. */
    public Map<String, Object> history(Device device, Metric metric, Instant dateFrom, Instant dateTo) {
        if (dateFrom == null || dateTo == null || !dateFrom.isBefore(dateTo)) {
            throw new IllegalArgumentException("dateFrom deve ser anterior a dateTo.");
        }
        // STH-Comet mantém o histórico; o banco da aplicação não duplica leituras.
        return sth.get()
                .uri(uriBuilder -> uriBuilder.path("/STH/v1/contextEntities/type/{type}/id/{id}/attributes/{attribute}")
                        .queryParam("dateFrom", dateFrom).queryParam("dateTo", dateTo)
			.queryParam("hLimit", 20)
			.queryParam("hOffset", 0)
                        .build(device.getFiwareType(), device.getFiwareId(), attributeName(metric)))
                .header("Fiware-Service", device.getFiwareService())
                .header("Fiware-ServicePath", device.getFiwareServicePath())
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    /** Traduz a métrica interna para o nome de atributo esperado pelo sensor. */
    private String attributeName(Metric metric) {
        return switch (metric) {
            case TEMPERATURE -> "temperature";
            case HUMIDITY -> "humidity";
            case LUMINOSITY -> "luminosity";
        };
    }
}
