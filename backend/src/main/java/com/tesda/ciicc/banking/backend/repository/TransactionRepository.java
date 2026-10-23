package com.tesda.ciicc.banking.backend.repository;

import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Integer>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findByAccountNumberOrderByIdAsc(String accountNumber);

    Page<Transaction> findByAccountNumberOrderByIdAsc(String accountNumber, Pageable pageable);

    Optional<Transaction> findByIdAndAccountNumber(int id, String accountNumber);

    /**
     * The account's outbound history before one transaction, newest first.
     * {@code id < beforeId} excludes the row being evaluated: auto-increment
     * ids are increasing, so every prior row has a smaller id.
     */
    List<Transaction> findByIdLessThanAndAccountNumberAndTransactionTypeInOrderByIdDesc(
            int id, String accountNumber, Collection<TransactionType> transactionTypes, Pageable pageable);

    /** How many outbound transactions the account has since a point in time. */
    long countByAccountNumberAndTransactionTypeInAndCreatedAtGreaterThanEqual(
            String accountNumber, Collection<TransactionType> transactionTypes, Timestamp since);

    /**
     * The summed amount per account for the given types, in one grouped query.
     * Reconciliation needs the ledger total of every account at once, so this
     * replaces walking every transaction row.
     */
    @Query("""
            select t.accountNumber as accountNumber, sum(t.amount) as total
            from Transaction t
            where t.transactionType in :transactionTypes
            group by t.accountNumber
            """)
    List<AccountTypeTotal> sumAmountByAccount(@Param("transactionTypes") Collection<TransactionType> transactionTypes);

    /** One row of {@link #sumAmountByAccount}: an account and its summed amount. */
    interface AccountTypeTotal {
        String getAccountNumber();

        BigDecimal getTotal();
    }
}
