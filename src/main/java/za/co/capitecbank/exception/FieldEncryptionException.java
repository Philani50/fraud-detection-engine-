package za.co.capitecbank.exception;

public class FieldEncryptionException extends RuntimeException {

    public FieldEncryptionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
