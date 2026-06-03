package za.co.capitecbank.exception;

public class FraudEvaluationException extends BusinessException {

    public FraudEvaluationException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public FraudEvaluationException(final String message) {
        super(message);
    }
}
