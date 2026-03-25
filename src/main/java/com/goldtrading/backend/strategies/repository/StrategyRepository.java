package com.goldtrading.backend.strategies.repository;

import com.goldtrading.backend.strategies.domain.entity.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StrategyRepository extends JpaRepository<Strategy, UUID>, JpaSpecificationExecutor<Strategy> {
    List<Strategy> findByIdIn(Collection<UUID> ids);
    java.util.Optional<Strategy> findByCodeIgnoreCase(String code);
    List<Strategy> findByActiveTrueOrderByNameViAsc();
}

