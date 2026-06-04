package za.co.capitecbank.adapter;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;

/**
 * Contract for fetching a client profile from an external service.
 *
 * <p>Returns a {@code CompletableFuture} so Resilience4j's {@code @TimeLimiter} can enforce
 * a timeout. The result is wrapped in {@code Optional} — an empty Optional means the profile
 * was not found; rules that require the profile will skip gracefully rather than throw.
 */
public interface ClientProfileAdapter {

    CompletableFuture<Optional<ClientProfileEntity>> fetchProfile(String clientId);
}
