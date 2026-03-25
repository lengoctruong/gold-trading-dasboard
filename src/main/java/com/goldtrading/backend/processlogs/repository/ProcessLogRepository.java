package com.goldtrading.backend.processlogs.repository;

import com.goldtrading.backend.processlogs.domain.entity.ProcessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ProcessLogRepository extends JpaRepository<ProcessLog, UUID>, JpaSpecificationExecutor<ProcessLog> {
    Page<ProcessLog> findByActionTypeContainingIgnoreCaseOrMessageContainingIgnoreCase(String actionType, String message, Pageable pageable);
}
