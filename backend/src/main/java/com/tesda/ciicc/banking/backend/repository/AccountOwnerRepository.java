package com.tesda.ciicc.banking.backend.repository;

import com.tesda.ciicc.banking.backend.model.AccountOwner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOwnerRepository extends JpaRepository<AccountOwner, Integer> {

    /** The service-layer ownership check: one indexed lookup, no locking needed. */
    boolean existsByUserIdAndAccountNumber(int userId, String accountNumber);
}
