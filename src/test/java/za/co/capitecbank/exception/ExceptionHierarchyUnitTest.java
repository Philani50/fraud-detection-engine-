package za.co.capitecbank.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExceptionHierarchyUnitTest {

    @Test
    void fraudEvaluationException_shouldBeInstanceOfBusinessException() {
        final FraudEvaluationException ex = new FraudEvaluationException("test");
        assertThat(ex).isInstanceOf(BusinessException.class);
    }

    @Test
    void businessException_shouldBeInstanceOfRuntimeException() {
        final FraudEvaluationException ex = new FraudEvaluationException("test");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
