package com.tesda.ciicc.banking.backend.repository;

import com.tesda.ciicc.banking.backend.model.AnomalyFlag;
import com.tesda.ciicc.banking.backend.model.AnomalyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyFlagRepository extends JpaRepository<AnomalyFlag, Integer> {

    Page<AnomalyFlag> findByStatus(AnomalyStatus status, Pageable pageable);
}
