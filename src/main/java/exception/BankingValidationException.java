package exception;

public class BankingValidationException extends BankingException {
    private static final long serialVersionUID = 1L;

    public BankingValidationException(String message) {
        super(message);
    }
}
