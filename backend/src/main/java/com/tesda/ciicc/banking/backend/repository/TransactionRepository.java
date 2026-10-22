package com.tesda.ciicc.banking.backend.repository;

import com.tesda.ciicc.banking.backend.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    List<Transaction> findByAccountNumberOrderByIdAsc(String accountNumber);

    Page<Transaction> findByAccountNumberOrderByIdAsc(String accountNumber, Pageable pageable);
}
