package za.co.capitecbank.adapter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import za.co.capitecbank.adapter.ClientProfileProxyClient;
import za.co.capitecbank.exception.ClientProfileServiceException;

/**
 * Builds and registers the {@link ClientProfileProxyClient} Spring bean.
 *
 * <p>Configures a {@code RestClient} with the base URL and timeouts from
 * {@link ClientProfileProperties}, adds an {@code X-Channel-Source} header so the
 * downstream service knows which caller is invoking it, and registers a 401 handler
 * that throws {@link za.co.capitecbank.exception.ClientProfileServiceException} instead
 * of letting the raw HTTP error propagate.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ClientProfileProperties.class)
public class ClientProfileProxyConfig {

    private static final String UNAUTHORIZED_ERROR = "Client profile service returned 401 Unauthorized";

    @Value("${spring.application.name:fraud-detection-engine}")
    private String applicationName;

    private final ClientProfileProperties properties;

    @Bean
    ClientProfileProxyClient clientProfileProxyClient() {
        final RestClient restClient = RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeader("X-Channel-Source", applicationName)
                .defaultStatusHandler(status -> status == HttpStatus.UNAUTHORIZED, (request, response) -> {
                    log.error(UNAUTHORIZED_ERROR);
                    throw new ClientProfileServiceException(UNAUTHORIZED_ERROR);
                })
                .build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ClientProfileProxyClient.class);
    }
}
