package com.tesda.ciicc.banking.backend.repository;

import com.tesda.ciicc.banking.backend.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Integer> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Locks the row until the surrounding transaction ends. Every caller must
     * request this lock on its own, so transfer can control the order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    List<Account> findAllByOrderByAccountNumberAsc();

    /** The same list, one page at a time — the ADMIN view in {@code GET /api/accounts}. */
    Page<Account> findAllByOrderByAccountNumberAsc(Pageable pageable);

    /**
     * The accounts owned by one user, by account number — the CUSTOMER view in
     * {@code GET /api/accounts}. A subquery rather than a join keeps the count
     * query exact: one account can only have one owner row, but the count is
     * taken over accounts either way.
     */
    @Query(value = """
            select a from Account a
            where a.accountNumber in (select o.accountNumber from AccountOwner o where o.userId = :userId)
            order by a.accountNumber asc
            """,
            countQuery = """
            select count(a) from Account a
            where a.accountNumber in (select o.accountNumber from AccountOwner o where o.userId = :userId)
            """)
    Page<Account> findOwnedByUserId(@Param("userId") int userId, Pageable pageable);

    /**
     * Number, name and balance of every account, ordered by number. The
     * reconciliation check reads this rather than full entities: it needs three
     * columns per row and must not hydrate an account per row.
     */
    @Query("""
            select a.accountNumber as accountNumber, a.accountName as accountName, a.balance as balance
            from Account a
            order by a.accountNumber asc
            """)
    List<StoredBalance> findAllStoredBalances();

    /** One row of {@link #findAllStoredBalances}. */
    interface StoredBalance {
        String getAccountNumber();

        String getAccountName();

        BigDecimal getBalance();
    }
}
