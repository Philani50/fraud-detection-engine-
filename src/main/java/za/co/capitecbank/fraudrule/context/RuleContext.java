package za.co.capitecbank.fraudrule.context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.Getter;
import za.co.capitecbank.enums.TransactionType;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

public final class RuleContext {

    @Getter
    private final TransactionEntity transaction;

    private final String clientId;
    private final FraudPersistenceService fraudPersistenceService;
    private final Supplier<Optional<ClientProfileEntity>> clientProfileSupplier;
    private final String traceId;

    RuleContext(
            final TransactionEntity txn,
            final FraudPersistenceService persistenceService,
            final Supplier<Optional<ClientProfileEntity>> profileSupplier,
            final String correlationId) {
        this.transaction = txn;
        this.clientId = txn.getClientId();
        this.fraudPersistenceService = persistenceService;
        this.clientProfileSupplier = profileSupplier;
        this.traceId = correlationId;
    }

    public Optional<ClientProfileEntity> getClientProfile() {
        return clientProfileSupplier.get();
    }

    public List<TransactionEntity> getCashTransactionsSince(final LocalDateTime since, final BigDecimal belowAmount) {
        return fraudPersistenceService.findCashTransactionsByClientSince(
                clientId, List.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL), since, belowAmount, traceId);
    }

    public long getTransactionCountSince(final LocalDateTime since) {
        return fraudPersistenceService.countTransactionsByClientSince(clientId, since, traceId);
    }

    public List<TransactionEntity> getTransactionsBetween(final LocalDateTime from, final LocalDateTime to) {
        return fraudPersistenceService.findTransactionsByClientBetween(clientId, from, to, traceId);
    }
}
