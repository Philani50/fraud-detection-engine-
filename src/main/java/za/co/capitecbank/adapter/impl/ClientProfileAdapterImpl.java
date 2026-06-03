package za.co.capitecbank.adapter.impl;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import za.co.capitecbank.adapter.ClientProfileAdapter;
import za.co.capitecbank.adapter.ClientProfileProxyClient;
import za.co.capitecbank.exception.ClientProfileServiceException;
import za.co.capitecbank.mapper.ClientProfileMapper;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientProfileAdapterImpl implements ClientProfileAdapter {

    private static final String METRIC_ERRORS = "client.profile.service.errors";

    private final ClientProfileProxyClient clientProfileProxyClient;
    private final ClientProfileMapper clientProfileMapper;
    private final MeterRegistry meterRegistry;

    @Override
    @Cacheable(value = "clientProfiles", key = "#clientId")
    public CompletableFuture<Optional<ClientProfileEntity>> fetchProfile(final String clientId) {
        log.info("Fetching client profile [clientId={}]", clientId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                final HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                final var response = clientProfileProxyClient.getClientProfile(headers, clientId);
                log.info("Client profile fetched successfully [clientId={}]", clientId);
                return Optional.of(clientProfileMapper.toEntity(response));
            } catch (HttpStatusCodeException ex) {
                log.error(
                        "Client profile service returned status {} [clientId={}]",
                        ex.getStatusCode().value(),
                        clientId);
                meterRegistry
                        .counter(
                                METRIC_ERRORS,
                                "statusCode",
                                String.valueOf(ex.getStatusCode().value()))
                        .increment();
                throw new ClientProfileServiceException(ex.getMessage(), ex);
            } catch (RestClientException ex) {
                throw ex;
            }
        });
    }
}
