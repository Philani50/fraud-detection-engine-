package za.co.capitecbank.exception;

public class ClientProfileServiceException extends BusinessException {

    public ClientProfileServiceException(final String message) {
        super(message);
    }

    public ClientProfileServiceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
