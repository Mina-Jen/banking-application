package exception;

public class AccountAlreadyExistsException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AccountAlreadyExistsException(String message) {
        super(message);
    }
}
