package za.co.capitecbank.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlertEntity, UUID> {}
