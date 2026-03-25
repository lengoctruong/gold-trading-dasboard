package com.goldtrading.backend.mt5accounts.dto;

import com.goldtrading.backend.common.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Mt5AccountResponse(UUID id, UUID userId, String accountNumber, String broker, String server,
                                 AccountType accountType, VerificationStatus verificationStatus, String verificationMessage,
                                 UUID strategyId, String timeframe, UUID riskRuleId, AccountStatus status,
                                 AdminActionState adminAction, UUID assignedPortId, OffsetDateTime submittedAt,
                                 OffsetDateTime startedAt, OffsetDateTime stoppedAt, OffsetDateTime updatedAt,
                                 String userFullName, String userEmail, String strategyCode, String riskRuleCode, String assignedPortCode) {}

