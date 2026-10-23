package com.tesda.ciicc.banking.backend.repository;

import com.tesda.ciicc.banking.backend.model.IdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Integer> {

    /**
     * Locking read on purpose: after a duplicate-key INSERT fails, an ordinary
     * SELECT would use the transaction's older snapshot and could miss the row
     * the other request just committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from IdempotencyKey k where k.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyKey> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey);
}
