package za.co.capitecbank.fraudrule.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@ExtendWith(MockitoExtension.class)
class RuleContextUnitTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String TRACE_ID = "trace-001";

    @Mock
    private FraudPersistenceService fraudPersistenceService;

    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = TransactionEntity.builder().clientId(CLIENT_ID).build();
    }

    @Test
    void getClientProfile_shouldCallSupplierOnce_whenCalledMultipleTimes() {
        final ClientProfileEntity profile =
                ClientProfileEntity.builder().clientId(CLIENT_ID).build();
        when(fraudPersistenceService.findClientProfile(CLIENT_ID, TRACE_ID)).thenReturn(Optional.of(profile));

        final RuleContext context = new RuleContext(
                transaction,
                fraudPersistenceService,
                memoize(() -> fraudPersistenceService.findClientProfile(CLIENT_ID, TRACE_ID)),
                TRACE_ID);

        context.getClientProfile();
        context.getClientProfile();
        context.getClientProfile();

        verify(fraudPersistenceService, times(1)).findClientProfile(CLIENT_ID, TRACE_ID);
    }

    @Test
    void getCashTransactionsSince_shouldDelegateToPersistenceService() {
        final LocalDateTime since = LocalDateTime.now().minusHours(24);
        final BigDecimal threshold = new BigDecimal("10000");
        when(fraudPersistenceService.findCashTransactionsByClientSince(
                        eq(CLIENT_ID), anyList(), eq(since), eq(threshold), eq(TRACE_ID)))
                .thenReturn(List.of());

        final RuleContext context = buildContext();
        final var result = context.getCashTransactionsSince(since, threshold);

        assertThat(result).isEmpty();
        verify(fraudPersistenceService)
                .findCashTransactionsByClientSince(eq(CLIENT_ID), anyList(), eq(since), eq(threshold), eq(TRACE_ID));
    }

    @Test
    void getTransactionCountSince_shouldDelegateToPersistenceService() {
        final LocalDateTime since = LocalDateTime.now().minusHours(1);
        when(fraudPersistenceService.countTransactionsByClientSince(CLIENT_ID, since, TRACE_ID))
                .thenReturn(7L);

        final RuleContext context = buildContext();
        assertThat(context.getTransactionCountSince(since)).isEqualTo(7L);
    }

    @Test
    void getTransactionsBetween_shouldDelegateToPersistenceService() {
        final LocalDateTime from = LocalDateTime.now().minusMinutes(30);
        final LocalDateTime to = LocalDateTime.now();
        when(fraudPersistenceService.findTransactionsByClientBetween(CLIENT_ID, from, to, TRACE_ID))
                .thenReturn(List.of());

        final RuleContext context = buildContext();
        assertThat(context.getTransactionsBetween(from, to)).isEmpty();
    }

    private RuleContext buildContext() {
        final Supplier<Optional<ClientProfileEntity>> supplier =
                memoize(() -> fraudPersistenceService.findClientProfile(CLIENT_ID, TRACE_ID));
        return new RuleContext(transaction, fraudPersistenceService, supplier, TRACE_ID);
    }

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
