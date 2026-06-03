package za.co.capitecbank.fraudrule.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@ExtendWith(MockitoExtension.class)
class RuleContextFactoryUnitTest {

    @Mock
    private FraudPersistenceService fraudPersistenceService;

    @InjectMocks
    private RuleContextFactory factory;

    @Test
    void create_shouldReturnRuleContextWithCorrectTransaction() {
        final TransactionEntity transaction =
                TransactionEntity.builder().clientId("CLIENT-001").build();

        final RuleContext context = factory.create(transaction, "trace-001");

        assertThat(context).isNotNull();
        assertThat(context.getTransaction()).isEqualTo(transaction);
    }
}
