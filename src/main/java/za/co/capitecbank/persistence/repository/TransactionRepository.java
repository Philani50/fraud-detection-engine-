package za.co.capitecbank.persistence.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.capitecbank.enums.TransactionType;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    long countByClientIdAndTimestampAfter(String clientId, LocalDateTime after);

    List<TransactionEntity> findByClientIdAndTransactionTypeInAndTimestampAfterAndAmountLessThan(
            String clientId, List<TransactionType> transactionTypes, LocalDateTime after, BigDecimal amount);

    List<TransactionEntity> findByClientIdAndTimestampBetweenOrderByTimestampAsc(
            String clientId, LocalDateTime start, LocalDateTime end);
}
