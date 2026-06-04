package za.co.capitecbank.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitecbank.enums.TransactionType;
import za.co.capitecbank.exception.FraudDatabaseException;
import za.co.capitecbank.exception.FraudEvaluationException;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;
import za.co.capitecbank.persistence.repository.ClientProfileRepository;
import za.co.capitecbank.persistence.repository.FraudAlertRepository;
import za.co.capitecbank.persistence.repository.TransactionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudPersistenceService {

    private final TransactionRepository transactionRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FraudAlertRepository fraudAlertRepository;

    // Returns how many transactions a client has made since a given point in time — used by velocity rules
    @Transactional(readOnly = true)
    public long countTransactionsByClientSince(
            final String clientId, final LocalDateTime after, final String correlationId) {
        log.debug("Counting transactions [correlationId={}, clientId={}]", correlationId, clientId);
        try {
            final long count = transactionRepository.countByClientIdAndTimestampAfter(clientId, after);
            log.debug("Counted {} transaction(s) [correlationId={}]", count, correlationId);
            return count;
        } catch (Exception ex) {
            log.error("Error counting transactions [correlationId={}]: {}", correlationId, ex.getMessage());
            throw new FraudDatabaseException(ex.getMessage());
        }
    }

    // Returns cash transactions (deposits/withdrawals) for a client below a given amount — used by structuring rules
    @Transactional(readOnly = true)
    public List<TransactionEntity> findCashTransactionsByClientSince(
            final String clientId,
            final List<TransactionType> types,
            final LocalDateTime after,
            final BigDecimal belowAmount,
            final String correlationId) {
        log.debug("Retrieving cash transactions [correlationId={}, clientId={}]", correlationId, clientId);
        try {
            final List<TransactionEntity> result =
                    transactionRepository.findByClientIdAndTransactionTypeInAndTimestampAfterAndAmountLessThan(
                            clientId, types, after, belowAmount);
            log.debug("Retrieved {} cash transaction(s) [correlationId={}]", result.size(), correlationId);
            return result;
        } catch (Exception ex) {
            log.error("Error retrieving cash transactions [correlationId={}]: {}", correlationId, ex.getMessage());
            throw new FraudDatabaseException(ex.getMessage());
        }
    }

    // Returns all transactions for a client within a time window — used by rapid movement rules
    @Transactional(readOnly = true)
    public List<TransactionEntity> findTransactionsByClientBetween(
            final String clientId, final LocalDateTime start, final LocalDateTime end, final String correlationId) {
        log.debug("Retrieving transactions in window [correlationId={}, clientId={}]", correlationId, clientId);
        try {
            final List<TransactionEntity> result =
                    transactionRepository.findByClientIdAndTimestampBetweenOrderByTimestampAsc(clientId, start, end);
            log.debug("Retrieved {} transaction(s) in window [correlationId={}]", result.size(), correlationId);
            return result;
        } catch (Exception ex) {
            log.error("Error retrieving transactions in window [correlationId={}]: {}", correlationId, ex.getMessage());
            throw new FraudDatabaseException(ex.getMessage());
        }
    }

    // Looks up the client's profile (income, last update date, etc.) — used by profile and spend rules
    @Transactional(readOnly = true)
    public Optional<ClientProfileEntity> findClientProfile(final String clientId, final String correlationId) {
        log.debug("Retrieving client profile [correlationId={}, clientId={}]", correlationId, clientId);
        try {
            final Optional<ClientProfileEntity> result = clientProfileRepository.findByClientId(clientId);
            log.debug("Client profile found={} [correlationId={}]", result.isPresent(), correlationId);
            return result;
        } catch (Exception ex) {
            log.error("Error retrieving client profile [correlationId={}]: {}", correlationId, ex.getMessage());
            throw new FraudDatabaseException(ex.getMessage());
        }
    }

    // Persists the incoming transaction so it is available for historical rule lookups
    @Transactional
    public TransactionEntity saveTransaction(final TransactionEntity transaction, final String correlationId) {
        log.info("Saving transaction [correlationId={}, clientId={}]", correlationId, transaction.getClientId());
        try {
            final TransactionEntity saved = transactionRepository.save(transaction);
            log.info("Transaction saved successfully [correlationId={}]", correlationId);
            return saved;
        } catch (Exception ex) {
            log.error("Error saving transaction [correlationId={}]: {}", correlationId, ex.getMessage());
            throw new FraudDatabaseException(ex.getMessage());
        }
    }

    // Saves a fraud alert raised by a rule — duplicate alert keys are caught and surfaced as evaluation errors
    @Transactional
    public FraudAlertEntity saveAlert(final FraudAlertEntity alert, final String correlationId) {
        log.info("Saving fraud alert [correlationId={}, clientId={}]", correlationId, alert.getClientId());
        try {
            final FraudAlertEntity saved = fraudAlertRepository.save(alert);
            log.info("Fraud alert saved successfully [correlationId={}]", correlationId);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.error("Duplicate alert key violation [correlationId={}]: {}", correlationId, ex.getMessage());
            throw new FraudEvaluationException("Duplicate alert key violation", ex);
        } catch (Exception ex) {
            log.error("Error saving fraud alert [correlationId={}]: {}", correlationId, ex.getMessage());
            throw new FraudDatabaseException(ex.getMessage());
        }
    }
}
