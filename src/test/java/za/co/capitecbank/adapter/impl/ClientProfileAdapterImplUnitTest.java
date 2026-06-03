package za.co.capitecbank.adapter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import za.co.capitecbank.adapter.ClientProfileProxyClient;
import za.co.capitecbank.adapter.model.ClientProfileResponse;
import za.co.capitecbank.exception.ClientProfileServiceException;
import za.co.capitecbank.mapper.ClientProfileMapper;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;

@ExtendWith(MockitoExtension.class)
class ClientProfileAdapterImplUnitTest {

    @Mock
    private ClientProfileProxyClient clientProfileProxyClient;

    @Mock
    private ClientProfileMapper clientProfileMapper;

    private SimpleMeterRegistry meterRegistry;
    private ClientProfileAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adapter = new ClientProfileAdapterImpl(clientProfileProxyClient, clientProfileMapper, meterRegistry);
    }

    @Test
    void fetchProfile_successfulResponse_shouldReturnProfile() throws Exception {
        final ClientProfileResponse response =
                ClientProfileResponse.builder().clientId("CLIENT-001").build();
        final ClientProfileEntity entity =
                ClientProfileEntity.builder().clientId("CLIENT-001").build();

        when(clientProfileProxyClient.getClientProfile(any(HttpHeaders.class), anyString()))
                .thenReturn(response);
        when(clientProfileMapper.toEntity(response)).thenReturn(entity);

        final Optional<ClientProfileEntity> result =
                adapter.fetchProfile("CLIENT-001").get();

        assertThat(result).isPresent();
        assertThat(result.get().getClientId()).isEqualTo("CLIENT-001");
        verify(clientProfileMapper).toEntity(response);
    }

    @Test
    void fetchProfile_httpErrorResponse_shouldIncrementErrorMetricAndThrow() {
        when(clientProfileProxyClient.getClientProfile(any(HttpHeaders.class), anyString()))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.fetchProfile("CLIENT-001").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ClientProfileServiceException.class);

        assertThat(meterRegistry
                        .counter("client.profile.service.errors", "statusCode", "503")
                        .count())
                .isEqualTo(1.0);
    }
}
