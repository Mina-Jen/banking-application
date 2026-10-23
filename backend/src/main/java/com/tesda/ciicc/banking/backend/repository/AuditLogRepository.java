package com.tesda.ciicc.banking.backend.repository;

import com.tesda.ciicc.banking.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {
}
