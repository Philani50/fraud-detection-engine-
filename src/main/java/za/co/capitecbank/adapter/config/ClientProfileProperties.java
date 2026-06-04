package za.co.capitecbank.adapter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the external Client Profile service.
 *
 * <p>Bound from {@code application.yml} under:
 * {@code app.http-configs.rest.client-profile-service}.
 *
 * @param url base URL of the Client Profile service
 * @param connectTimeout maximum time to wait when establishing a connection
 * @param readTimeout maximum time to wait for a response
 */
@ConfigurationProperties(prefix = "app.http-configs.rest.client-profile-service")
public record ClientProfileProperties(String url, Duration connectTimeout, Duration readTimeout) {}
