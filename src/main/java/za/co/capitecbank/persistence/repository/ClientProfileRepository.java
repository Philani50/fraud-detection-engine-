package za.co.capitecbank.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;

@Repository
public interface ClientProfileRepository extends JpaRepository<ClientProfileEntity, Long> {

    Optional<ClientProfileEntity> findByClientId(String clientId);
}
