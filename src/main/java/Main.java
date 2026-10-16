import exception.InvalidAmountException;
import exception.BankingException;
import model.Account;
import model.Transaction;
import service.BankingService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static final BankingService banking = new BankingService();
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        System.out.println("====================================");
        System.out.println("        BANKING APPLICATION");
        System.out.println("====================================");

        while (true) {
            printMenu();
            String choice = readLine(in, "Enter your choice: ");
            try {
                switch (choice) {
                    case "0" -> {
                        System.out.println("Goodbye!");
                        return;
                    }
                    case "1" -> createAccount(in);
                    case "2" -> balanceInquiry(in);
                    case "3" -> listAccounts();
                    case "4" -> deposit(in);
                    case "5" -> withdraw(in);
                    case "6" -> transfer(in);
                    case "7" -> transactionHistory(in);
                    default -> System.out.println("Invalid choice. Enter a number from 0 to 7.");
                }
            } catch (BankingException e) {
                System.out.println("ERROR: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("DATABASE ERROR: " + e.getMessage());
                System.out.println("Check that MySQL (XAMPP) is running and that banking_db exists.");
            }
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("1. Create Account");
        System.out.println("2. Balance Inquiry");
        System.out.println("3. List Accounts");
        System.out.println("4. Deposit");
        System.out.println("5. Withdraw");
        System.out.println("6. Transfer");
        System.out.println("7. Transaction History");
        System.out.println("0. Exit");
    }

    // ---------- 1. Create Account ----------

    private static void createAccount(Scanner in)
            throws SQLException, BankingException {
        String accountNumber = readLine(in, "Account Number: ");
        String accountName = readLine(in, "Account Holder Name: ");
        BigDecimal initialDeposit = readAmount(in, "Initial Deposit: ₱");

        Account created = banking.createAccount(accountNumber, accountName, initialDeposit);
        System.out.printf("%nAccount Number : %s%n", created.getAccountNumber());
        System.out.printf("Account Name   : %s%n", created.getAccountName());
        System.out.printf("Initial Deposit: %s%n", money(created.getBalance()));
        System.out.println("Account created successfully!");
    }

    // ---------- 2. Balance Inquiry ----------

    private static void balanceInquiry(Scanner in)
            throws SQLException, BankingException {

        Account account = banking.getAccount(readLine(in, "Account Number: "));
        System.out.printf("%nAccount Number : %s%n", account.getAccountNumber());
        System.out.printf("Account Holder : %s%n", account.getAccountName());
        System.out.printf("Current Balance: %s%n", money(account.getBalance()));
    }

    // ---------- 3. List Accounts ----------

    private static void listAccounts() throws SQLException {
        List<Account> accounts = banking.listAccounts();
        System.out.printf("%n%-14s %-26s %15s%n", "ACCOUNT NO", "ACCOUNT HOLDER", "BALANCE");
        if (accounts.isEmpty()) {
            System.out.println("(no accounts registered)");
        } else {
            for (Account account : accounts) {
                System.out.printf("%-14s %-26s %15s%n",
                        account.getAccountNumber(), account.getAccountName(), money(account.getBalance()));
            }
        }
        System.out.println("Total accounts: " + accounts.size());
    }

    // ---------- 4. Deposit ----------

    private static void deposit(Scanner in)
            throws SQLException, BankingException {
        String accountNumber = readLine(in, "Account Number: ");
        BigDecimal amount = readAmount(in, "Deposit Amount: ₱");

        Transaction saved = banking.deposit(accountNumber, amount);
        System.out.printf("%nPrevious Balance: %s%n", money(saved.getBalance().subtract(saved.getAmount())));
        System.out.printf("Deposit Amount  : %s%n", money(saved.getAmount()));
        System.out.printf("New Balance     : %s%n", money(saved.getBalance()));
        System.out.println("Deposit successful! (Reference " + saved.getTransactionReference() + ")");
    }

    // ---------- 5. Withdraw ----------

    private static void withdraw(Scanner in)
            throws SQLException, BankingException {

        String accountNumber = readLine(in, "Account Number: ");
        BigDecimal amount = readAmount(in, "Withdrawal Amount: ₱");

        Transaction saved = banking.withdraw(accountNumber, amount);
        System.out.printf("%nPrevious Balance  : %s%n", money(saved.getBalance().add(saved.getAmount())));
        System.out.printf("Withdrawal Amount : %s%n", money(saved.getAmount()));
        System.out.printf("New Balance       : %s%n", money(saved.getBalance()));
        System.out.println("Withdrawal successful! (Reference " + saved.getTransactionReference() + ")");
    }

    // ---------- 6. Transfer ----------

    private static void transfer(Scanner in)
            throws SQLException, BankingException {

        String source = readLine(in, "From Account Number: ");
        String destination = readLine(in, "To Account Number: ");
        BigDecimal amount = readAmount(in, "Transfer Amount: ₱");

        Transaction saved = banking.transfer(source, destination, amount);
        System.out.printf("%nFrom Account       : %s%n", source);
        System.out.printf("To Account         : %s%n", destination);
        System.out.printf("Amount             : %s%n", money(amount));
        System.out.printf("New source balance : %s%n", money(saved.getBalance()));
        System.out.println("Transfer successful! (Reference " + saved.getTransactionReference() + ")");
    }

    // ---------- 7. Transaction History ----------

    private static void transactionHistory(Scanner in)
            throws SQLException, BankingException {

        String accountNumber = readLine(in, "Account Number: ");
        List<Transaction> transactions = banking.getTransactionHistory(accountNumber);

        System.out.printf("%nTransaction history of %s%n", accountNumber);
        System.out.printf("%-9s %-9s %13s %-19s %-10s %13s%n",
                "REF", "TYPE", "AMOUNT", "DATE", "REF ACCT", "BALANCE");
        if (transactions.isEmpty()) {
            System.out.println("(no transactions recorded)");
            return;
        }
        for (Transaction t : transactions) {
            String referenceAccount = t.getReferenceAccount() == null ? "-" : t.getReferenceAccount();
            System.out.printf("%-9s %-9s %13s %-19s %-10s %13s%n",
                    t.getTransactionReference(),
                    t.getTransactionType(),
                    money(t.getAmount()),
                    t.getCreatedAt().toLocalDateTime().format(TIMESTAMP),
                    referenceAccount,
                    money(t.getBalance()));
        }
    }

    // ---------- helpers ----------

    private static String money(BigDecimal amount) {
        return "₱" + MONEY.format(amount);
    }

    private static String readLine(Scanner in, String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) {
            System.out.println("No more input; exiting.");
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    private static BigDecimal readAmount(Scanner in, String prompt) throws InvalidAmountException {
        String input = readLine(in, prompt);
        try {
            return new BigDecimal(input);
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("\"" + input + "\" is not a valid amount.");
        }
    }
}
