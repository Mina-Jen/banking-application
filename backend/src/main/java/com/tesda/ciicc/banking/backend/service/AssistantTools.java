package com.tesda.ciicc.banking.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tesda.ciicc.banking.backend.ai.AssistantModelClient.AssistantToolSpec;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.InsufficientFundsException;
import com.tesda.ciicc.banking.backend.exception.InvalidAmountException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import com.tesda.ciicc.banking.backend.web.Money;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tools the chat assistant may call. All of them are read-only and take
 * the authenticated {@link CurrentUser} as a separate parameter: the model can
 * choose an account number, a date, or an amount, but it can never choose
 * WHOSE data to read. Every account lookup goes through
 * {@link BankingService}, so the same ownership rule and the same generic 404
 * used by the REST API apply here.
 *
 * <p>{@link #PROPOSE_TRANSFER} is the only tool that could ever lead to money
 * moving, and it deliberately stops at a proposal. It validates the source
 * account and the amount, returns a description of the transfer, and never
 * calls {@link BankingService#transfer}. Confirmation stays with the customer
 * and the ordinary, idempotent transfer endpoint.</p>
 *
 * <p>Tool arguments are untrusted model output. Only the fixed fields below
 * are read; anything else — including a {@code userId} the model might invent
 * — is ignored. Failures are returned to the model as an error object so it
 * can explain the problem, not thrown at the HTTP caller.</p>
 */
@Service
public class AssistantTools {

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    public static final String GET_BALANCE = "get_balance";
    public static final String SEARCH_TRANSACTIONS = "search_transactions";
    public static final String SUMMARIZE_SPENDING = "summarize_spending";
    public static final String PROPOSE_TRANSFER = "propose_transfer";

    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int MAX_SEARCH_LIMIT = 50;
    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 20;   // VARCHAR(20)
    private static final int MAX_INTEGER_DIGITS = 13;          // DECIMAL(15,2)

    private static final List<TransactionType> OUTBOUND =
            List.of(TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT);

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    static final String PROPOSAL_NOTE =
            "This is only a proposal; no money has moved. Confirm it in the app with "
                    + "POST /api/transfers and an Idempotency-Key.";

    private static final Map<String, List<String>> KNOWN_ARGUMENTS = Map.of(
            GET_BALANCE, List.of("accountNumber"),
            SEARCH_TRANSACTIONS, List.of("accountNumber", "transactionType", "category",
                    "fromDate", "toDate", "minAmount", "maxAmount", "limit"),
            SUMMARIZE_SPENDING, List.of("accountNumber", "fromDate", "toDate"),
            PROPOSE_TRANSFER, List.of("sourceAccountNumber", "destinationAccountNumber", "amount"));

    private final BankingService banking;
    private final TransactionRepository transactions;
    private final ObjectMapper objectMapper;
    private final List<AssistantToolSpec> specifications;

    public AssistantTools(BankingService banking, TransactionRepository transactions,
                          ObjectMapper objectMapper) {
        this.banking = banking;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.specifications = List.of(
                new AssistantToolSpec(GET_BALANCE,
                        "Return the balance of one of the signed-in customer's accounts, or of all "
                                + "their accounts when accountNumber is omitted. An ADMIN must supply "
                                + "accountNumber.",
                        objectSchema(properties(
                                "accountNumber", stringProperty(
                                        "Account number to read. Omit to read all of the caller's accounts.")),
                                List.of())),
                new AssistantToolSpec(SEARCH_TRANSACTIONS,
                        "Search the signed-in customer's transactions, newest first. All filters are "
                                + "optional. When accountNumber is omitted the search covers all of the "
                                + "customer's accounts. limit is 1-50 (default 10). category accepts one of "
                                + TransactionCategory.allowedCodes() + ", or UNCATEGORIZED.",
                        objectSchema(properties(
                                "accountNumber", stringProperty("Account number to search; omit for all the caller's accounts."),
                                "transactionType", stringProperty("One of DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT."),
                                "category", stringProperty("A category code, or UNCATEGORIZED for transactions with no category."),
                                "fromDate", stringProperty("First day to include, as YYYY-MM-DD."),
                                "toDate", stringProperty("Last day to include, as YYYY-MM-DD."),
                                "minAmount", stringProperty("Smallest amount to include, as a decimal string."),
                                "maxAmount", stringProperty("Largest amount to include, as a decimal string."),
                                "limit", integerProperty("Maximum transactions to return, 1-50 (default 10).")),
                                List.of())),
                new AssistantToolSpec(SUMMARIZE_SPENDING,
                        "Total the signed-in customer's outbound spending (withdrawals and outgoing "
                                + "transfers) by category, optionally for a date range. When accountNumber "
                                + "is omitted the summary covers all of the customer's accounts.",
                        objectSchema(properties(
                                "accountNumber", stringProperty("Account number to summarize; omit for all the caller's accounts."),
                                "fromDate", stringProperty("First day to include, as YYYY-MM-DD."),
                                "toDate", stringProperty("Last day to include, as YYYY-MM-DD.")),
                                List.of())),
                new AssistantToolSpec(PROPOSE_TRANSFER,
                        "Describe a transfer for the customer to confirm. This tool NEVER executes a "
                                + "transfer and never moves money. Use it only when the customer explicitly "
                                + "asks to transfer funds, then tell them they must confirm it in the app.",
                        objectSchema(properties(
                                "sourceAccountNumber", stringProperty("The caller's account the money would leave."),
                                "destinationAccountNumber", stringProperty("The account that would receive the money."),
                                "amount", stringProperty("Amount to transfer, as a decimal string with two decimals.")),
                                List.of("sourceAccountNumber", "destinationAccountNumber", "amount"))));
    }

    public List<AssistantToolSpec> specifications() {
        return specifications;
    }

    /**
     * Runs one tool call for the authenticated caller. Arguments outside the
     * tool's fixed vocabulary are ignored; a tool failure becomes an
     * {@code {"error": "..."}} result for the model instead of an HTTP error.
     */
    @Transactional(readOnly = true)
    public ToolExecution execute(CurrentUser caller, String toolName, JsonNode input) {
        JsonNode safeInput = input != null && input.isObject()
                ? input
                : JsonNodeFactory.instance.objectNode();
        String name = toolName == null ? "" : toolName;
        Map<String, Object> arguments = knownArguments(name, safeInput);

        try {
            return switch (name) {
                case GET_BALANCE -> getBalance(caller, safeInput, arguments);
                case SEARCH_TRANSACTIONS -> searchTransactions(caller, safeInput, arguments);
                case SUMMARIZE_SPENDING -> summarizeSpending(caller, safeInput, arguments);
                case PROPOSE_TRANSFER -> proposeTransfer(caller, safeInput, arguments);
                default -> error(name, arguments, "Unknown tool: " + name + ".");
            };
        } catch (BankingException e) {
            return error(name, arguments, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Assistant tool {} failed: {}", name, e.toString());
            return error(name, arguments, "The assistant could not read that information.");
        }
    }

    // =====================================================================
    //  Tools
    // =====================================================================

    private ToolExecution getBalance(CurrentUser caller, JsonNode input,
                                     Map<String, Object> arguments) throws BankingException {
        List<Account> accounts = resolveAccounts(caller, optionalText(input, "accountNumber"));

        List<Map<String, Object>> rows = accounts.stream().map(account -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountNumber", account.getAccountNumber());
            row.put("accountName", account.getAccountName());
            row.put("balance", Money.format(account.getBalance()));
            return row;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", rows);
        return new ToolExecution(GET_BALANCE, arguments, json(result), null);
    }

    private ToolExecution searchTransactions(CurrentUser caller, JsonNode input,
                                             Map<String, Object> arguments) throws BankingException {
        List<Account> accounts = resolveAccounts(caller, optionalText(input, "accountNumber"));
        TransactionType type = optionalType(input);
        CategoryChoice category = optionalCategory(input);
        LocalDate from = optionalDate(input, "fromDate");
        LocalDate to = optionalDate(input, "toDate");
        requireDateOrder(from, to);
        BigDecimal min = optionalMoney(input, "minAmount");
        BigDecimal max = optionalMoney(input, "maxAmount");
        int limit = optionalLimit(input, DEFAULT_SEARCH_LIMIT);

        List<String> accountNumbers = accountNumbers(accounts);
        List<Transaction> found = transactions.findAll(
                transactionSpec(accountNumbers, type, category.category(), category.uncategorized(),
                        from, to, min, max, null),
                PageRequest.of(0, limit, NEWEST_FIRST)).getContent();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountNumbers", accountNumbers);
        result.put("count", found.size());
        result.put("transactions", found.stream().map(AssistantTools::transactionView).toList());
        return new ToolExecution(SEARCH_TRANSACTIONS, arguments, json(result), null);
    }

    private ToolExecution summarizeSpending(CurrentUser caller, JsonNode input,
                                            Map<String, Object> arguments) throws BankingException {
        List<Account> accounts = resolveAccounts(caller, optionalText(input, "accountNumber"));
        LocalDate from = optionalDate(input, "fromDate");
        LocalDate to = optionalDate(input, "toDate");
        requireDateOrder(from, to);

        List<String> accountNumbers = accountNumbers(accounts);
        List<Transaction> found = transactions.findAll(
                transactionSpec(accountNumbers, null, null, false, from, to, null, null, OUTBOUND));

        Map<String, BigDecimal> totals = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : found) {
            String key = transaction.getCategory() == null
                    ? "UNCATEGORIZED"
                    : transaction.getCategory().name();
            totals.merge(key, transaction.getAmount(), BigDecimal::add);
            counts.merge(key, 1, Integer::sum);
            total = total.add(transaction.getAmount());
        }

        List<Map<String, Object>> categories = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", entry.getKey());
                    row.put("total", Money.format(entry.getValue()));
                    row.put("count", counts.get(entry.getKey()));
                    return row;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountNumbers", accountNumbers);
        result.put("from", from == null ? null : from.toString());
        result.put("to", to == null ? null : to.toString());
        result.put("totalSpent", Money.format(total));
        result.put("transactionCount", found.size());
        result.put("categories", categories);
        return new ToolExecution(SUMMARIZE_SPENDING, arguments, json(result), null);
    }

    /**
     * Validates a transfer the customer asked for and returns a proposal.
     * The destination is deliberately NOT looked up: checking whether an
     * arbitrary account exists would give the assistant an account-number
     * oracle. The real transfer endpoint validates the destination when the
     * customer confirms.
     */
    private ToolExecution proposeTransfer(CurrentUser caller, JsonNode input,
                                          Map<String, Object> arguments) throws BankingException {
        String source = requiredText(input, "sourceAccountNumber");
        String destination = requiredText(input, "destinationAccountNumber");
        String rawAmount = requiredText(input, "amount");

        // Ownership is settled before anything about the destination or amount
        // is discussed, so a foreign source still answers with the generic 404.
        Account account = banking.getAccount(caller, source);

        if (destination.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new BankingValidationException(
                    "destinationAccountNumber must be at most " + MAX_ACCOUNT_NUMBER_LENGTH + " characters.");
        }
        if (source.equalsIgnoreCase(destination)) {
            throw new BankingValidationException("Source and destination accounts must be different.");
        }

        BigDecimal amount = Money.parse(rawAmount, "Transfer amount");
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("Transfer amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Transfer amount must not use more than two decimal places.");
        }
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidAmountException(
                    "Amount is too large: a balance may have at most " + MAX_INTEGER_DIGITS + " digits.");
        }
        if (amount.compareTo(account.getBalance()) > 0) {
            throw new InsufficientFundsException("Amount " + amount.toPlainString()
                    + " exceeds the available balance of " + account.getBalance().toPlainString() + ".");
        }

        String formatted = Money.format(amount);
        TransferProposal proposal = new TransferProposal(source, destination, formatted, "PROPOSED", PROPOSAL_NOTE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "PROPOSED");
        result.put("executed", false);
        result.put("sourceAccountNumber", source);
        result.put("destinationAccountNumber", destination);
        result.put("amount", formatted);
        result.put("availableBalance", Money.format(account.getBalance()));
        result.put("note", PROPOSAL_NOTE);
        return new ToolExecution(PROPOSE_TRANSFER, arguments, json(result), proposal);
    }

    // =====================================================================
    //  Argument parsing (untrusted model input)
    // =====================================================================

    /** Only the tool's documented fields survive; unknown fields such as userId are dropped. */
    private Map<String, Object> knownArguments(String toolName, JsonNode input) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (String field : KNOWN_ARGUMENTS.getOrDefault(toolName, List.of())) {
            JsonNode value = input.get(field);
            if (value != null && !value.isNull()) {
                arguments.put(field, objectMapper.convertValue(value, Object.class));
            }
        }
        return arguments;
    }

    private static String optionalText(JsonNode input, String field) throws BankingValidationException {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BankingValidationException(field + " must be a string.");
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String requiredText(JsonNode input, String field) throws BankingValidationException {
        String text = optionalText(input, field);
        if (text == null) {
            throw new BankingValidationException(field + " is required.");
        }
        return text;
    }

    private static TransactionType optionalType(JsonNode input) throws BankingValidationException {
        String raw = optionalText(input, "transactionType");
        if (raw == null) {
            return null;
        }
        try {
            return TransactionType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BankingValidationException(
                    "transactionType must be one of: DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT.");
        }
    }

    private static CategoryChoice optionalCategory(JsonNode input) throws BankingValidationException {
        String raw = optionalText(input, "category");
        if (raw == null) {
            return new CategoryChoice(null, false);
        }
        if ("UNCATEGORIZED".equalsIgnoreCase(raw)) {
            return new CategoryChoice(null, true);
        }
        return new CategoryChoice(TransactionCategory.fromCode(raw).orElseThrow(() ->
                new BankingValidationException("category must be one of: "
                        + TransactionCategory.allowedCodes() + ", UNCATEGORIZED.")), false);
    }

    private static LocalDate optionalDate(JsonNode input, String field) throws BankingValidationException {
        String raw = optionalText(input, field);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new BankingValidationException(field + " must be a date in YYYY-MM-DD format.");
        }
    }

    private static void requireDateOrder(LocalDate from, LocalDate to) throws BankingValidationException {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BankingValidationException("fromDate must not be after toDate.");
        }
    }

    private static BigDecimal optionalMoney(JsonNode input, String field) throws BankingValidationException {
        String raw = optionalText(input, field);
        return Money.parse(raw, field);
    }

    private static int optionalLimit(JsonNode input, int defaultValue) throws BankingValidationException {
        JsonNode value = input.get("limit");
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        int limit;
        if (value.isIntegralNumber()) {
            limit = value.asInt();
        } else if (value.isTextual()) {
            try {
                limit = Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException e) {
                throw new BankingValidationException("limit must be a whole number between 1 and "
                        + MAX_SEARCH_LIMIT + ".");
            }
        } else {
            throw new BankingValidationException("limit must be a whole number between 1 and "
                    + MAX_SEARCH_LIMIT + ".");
        }
        if (limit < 1 || limit > MAX_SEARCH_LIMIT) {
            throw new BankingValidationException("limit must be between 1 and "
                    + MAX_SEARCH_LIMIT + ".");
        }
        return limit;
    }

    // =====================================================================
    //  Account scoping and queries
    // =====================================================================

    /**
     * The signed-in customer's accounts for a tool call: the named account
     * (ownership checked by BankingService) or every account they own. An
     * ADMIN must name one, because "all accounts" would mean the whole bank.
     */
    private List<Account> resolveAccounts(CurrentUser caller, String accountNumber)
            throws BankingException {
        if (accountNumber != null) {
            return List.of(banking.getAccount(caller, accountNumber));
        }
        if (caller.isAdmin()) {
            throw new BankingValidationException(
                    "accountNumber is required for an ADMIN. Ask which account to use.");
        }
        Page<Account> owned = banking.listAccounts(caller, 0, 100);
        if (owned.isEmpty()) {
            throw new BankingValidationException("You do not have any accounts yet.");
        }
        return List.copyOf(owned.getContent());
    }

    private static List<String> accountNumbers(List<Account> accounts) {
        return accounts.stream().map(Account::getAccountNumber).toList();
    }

    private static Specification<Transaction> transactionSpec(
            List<String> accountNumbers, TransactionType type, TransactionCategory category,
            boolean uncategorized, LocalDate from, LocalDate to, BigDecimal min, BigDecimal max,
            Collection<TransactionType> allowedTypes) {

        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("accountNumber").in(accountNumbers));
            if (allowedTypes != null) {
                predicates.add(root.get("transactionType").in(allowedTypes));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("transactionType"), type));
            }
            if (uncategorized) {
                predicates.add(builder.isNull(root.get("category")));
            } else if (category != null) {
                predicates.add(builder.equal(root.get("category"), category));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(from)));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), startOfDay(to.plusDays(1))));
            }
            if (min != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("amount"), min));
            }
            if (max != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("amount"), max));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Timestamp startOfDay(LocalDate date) {
        return Timestamp.from(date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static Map<String, Object> transactionView(Transaction transaction) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reference", transaction.getTransactionReference());
        row.put("accountNumber", transaction.getAccountNumber());
        row.put("type", transaction.getTransactionType().name());
        row.put("amount", Money.format(transaction.getAmount()));
        row.put("balanceAfter", Money.format(transaction.getBalance()));
        row.put("referenceAccount", transaction.getReferenceAccount());
        row.put("description", transaction.getDescription());
        row.put("category", transaction.getCategory() == null ? null : transaction.getCategory().name());
        row.put("createdAt", transaction.getCreatedAt().toInstant().toString());
        return row;
    }

    // =====================================================================
    //  Results and schema helpers
    // =====================================================================

    private ToolExecution error(String toolName, Map<String, Object> arguments, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        return new ToolExecution(toolName, arguments, json(result), null);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize a tool result.", e);
        }
    }

    private static Map<String, Object> properties(Object... pairs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            properties.put((String) pairs[i], pairs[i + 1]);
        }
        return properties;
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> integerProperty(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private record CategoryChoice(TransactionCategory category, boolean uncategorized) {
    }

    /**
     * What one tool call produced: the canonical argument view (unknown fields
     * removed), the JSON handed back to the model, and a transfer proposal
     * when the call was {@link #PROPOSE_TRANSFER} and it succeeded.
     */
    public record ToolExecution(String tool, Map<String, Object> arguments,
                                String resultJson, TransferProposal proposal) {
        public ToolExecution {
            arguments = Map.copyOf(arguments);
        }
    }
}
