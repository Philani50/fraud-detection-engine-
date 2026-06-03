package za.co.capitecbank.fraudrule.context;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Component
@RequiredArgsConstructor
public class RuleContextFactory {

    private final FraudPersistenceService fraudPersistenceService;

    // Builds a RuleContext for the given transaction — the client profile is loaded lazily and
    // cached so multiple rules can call getClientProfile() without hitting the database twice
    public RuleContext create(final TransactionEntity transaction, final String traceId) {
        final String clientId = transaction.getClientId();
        return new RuleContext(
                transaction,
                fraudPersistenceService,
                memoize(() -> fraudPersistenceService.findClientProfile(clientId, traceId)),
                traceId);
    }

    // Wraps a Supplier so the result is only fetched once — subsequent calls return the cached value
    private static <T> Supplier<T> memoize(final Supplier<T> delegate) {
        final AtomicReference<T> value = new AtomicReference<>();
        return () -> {
            if (value.get() == null) {
                value.compareAndSet(null, delegate.get());
            }
            return value.get();
        };
    }
}
