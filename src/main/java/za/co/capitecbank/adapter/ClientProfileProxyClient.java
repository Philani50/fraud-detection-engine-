package za.co.capitecbank.adapter;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import za.co.capitecbank.adapter.model.ClientProfileResponse;

/**
 * Declarative HTTP client for the external Client Profile service.
 *
 * <p>Spring 6 HTTP interfaces — this interface declares the remote endpoint and Spring
 * generates the HTTP implementation automatically. The base URL, timeouts, and auth
 * headers are configured in {@code ClientProfileProxyConfig}.
 *
 * <p>Parameters must NOT be {@code final} — Spring HTTP interface methods are abstract
 * and the {@code RedundantModifier} Checkstyle rule forbids final on abstract parameters.
 */
public interface ClientProfileProxyClient {

    @GetExchange("/v1/clients/{clientId}/profile")
    ClientProfileResponse getClientProfile(@RequestHeader HttpHeaders headers, @PathVariable String clientId);
}
