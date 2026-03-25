package com.goldtrading.backend.mt5accounts.service;

import com.goldtrading.backend.admin.dto.response.PagedDataResponse;
import com.goldtrading.backend.auditlogs.service.AuditLogService;
import com.goldtrading.backend.brokers.service.BrokerCatalogService;
import com.goldtrading.backend.common.*;
import com.goldtrading.backend.common.exception.BusinessException;
import com.goldtrading.backend.infrastructure.crypto.Mt5PasswordCryptoService;
import com.goldtrading.backend.infrastructure.runtime.BotRuntimeAdapter;
import com.goldtrading.backend.mt5accounts.domain.entity.MT5Account;
import com.goldtrading.backend.mt5accounts.dto.CreateMt5AccountRequest;
import com.goldtrading.backend.mt5accounts.dto.Mt5AccountResponse;
import com.goldtrading.backend.mt5accounts.dto.UpdateMt5AccountRequest;
import com.goldtrading.backend.mt5accounts.policy.Mt5AccountLifecyclePolicy;
import com.goldtrading.backend.mt5accounts.policy.PortLifecyclePolicy;
import com.goldtrading.backend.mt5accounts.repository.MT5AccountRepository;
import com.goldtrading.backend.notifications.service.NotificationService;
import com.goldtrading.backend.ports.domain.entity.PortMaster;
import com.goldtrading.backend.ports.repository.PortMasterRepository;
import com.goldtrading.backend.processlogs.service.ProcessLogService;
import com.goldtrading.backend.users.domain.entity.User;
import com.goldtrading.backend.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Mt5AccountService {
    private final MT5AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PortMasterRepository portMasterRepository;
    private final BotRuntimeAdapter runtimeAdapter;
    private final AuditLogService auditLogService;
    private final ProcessLogService processLogService;
    private final NotificationService notificationService;
    private final Mt5PasswordCryptoService passwordCryptoService;
    private final Mt5AccountLifecyclePolicy lifecyclePolicy;
    private final PortLifecyclePolicy portLifecyclePolicy;
    private final BrokerCatalogService brokerCatalogService;
    private final com.goldtrading.backend.strategies.repository.StrategyRepository strategyRepository;
    private final com.goldtrading.backend.riskrules.repository.RiskRuleRepository riskRuleRepository;

    public List<Mt5AccountResponse> myAccounts(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        return accountRepository.findByUserId(user.getId()).stream().filter(a -> a.getDeletedAt() == null).map(this::toResponse).toList();
    }

    public Mt5AccountResponse getMyAccount(String email, UUID id) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        if (!a.getUserId().equals(user.getId()) || a.getDeletedAt() != null) throw new BusinessException("ACCESS_DENIED", "Cannot access account");
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse create(String email, CreateMt5AccountRequest req) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        if (accountRepository.existsByAccountNumberIgnoreCase(req.accountNumber())) {
            throw new BusinessException("DUPLICATE_MT5_ACCOUNT_NUMBER", "Số tài khoản MT5 đã tồn tại");
        }
        var selectedServer = brokerCatalogService.resolveActiveServer(req.brokerId(), req.serverId());
        MT5Account a = new MT5Account();
        a.setUserId(user.getId());
        a.setAccountNumber(req.accountNumber());
        a.setEncryptedPassword(passwordCryptoService.encrypt(req.password()));
        a.setBroker(selectedServer.getBroker().getName());
        a.setServer(selectedServer.getName());
        a.setAccountType(req.accountType());
        a.setVerificationStatus(VerificationStatus.UNVERIFIED);
        a.setStatus(AccountStatus.PENDING);
        a.setAdminAction(AdminActionState.PENDING_ADMIN);
        a.setLastConfigUpdatedAt(OffsetDateTime.now());
        accountRepository.save(a);
        auditLogService.log("USER", user.getId().toString(), user.getFullName(), "CREATE_MT5_ACCOUNT", "MT5_ACCOUNT", a.getId().toString(), "success", "Account created", null);
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse updateMyAccount(String email, UUID id, UpdateMt5AccountRequest req) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        if (!a.getUserId().equals(user.getId())) throw new BusinessException("ACCESS_DENIED", "Cannot access account");

        if (req.strategyId() != null || req.timeframe() != null || req.riskRuleId() != null) {
            lifecyclePolicy.ensureCanModifyConfig(a);
        }

        if (req.strategyId() != null) a.setStrategyId(req.strategyId());
        if (req.timeframe() != null) a.setTimeframe(req.timeframe());
        if (req.riskRuleId() != null) a.setRiskRuleId(req.riskRuleId());
        if (req.broker() != null) a.setBroker(req.broker());
        if (req.server() != null) a.setServer(req.server());
        a.setLastConfigUpdatedAt(OffsetDateTime.now());
        if (a.getStatus() == AccountStatus.STOPPED) {
            a.setStatus(AccountStatus.PENDING);
            a.setAdminAction(AdminActionState.PENDING_RECONFIGURE);
            notificationService.create(a.getUserId(), "info", "Cấu hình đã thay đổi", "Tài khoản đã chuyển về trạng thái chờ xử lý.");
        }
        return toResponse(a);
    }

    @Transactional
    public void deleteMyAccount(String email, UUID id) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        if (!a.getUserId().equals(user.getId())) throw new BusinessException("ACCESS_DENIED", "Cannot access account");
        lifecyclePolicy.ensureCanDelete(a);
        a.setDeletedAt(OffsetDateTime.now());
    }

    @Transactional
    public Mt5AccountResponse verifyMyAccount(String email, UUID id) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        if (!a.getUserId().equals(user.getId())) throw new BusinessException("ACCESS_DENIED", "Cannot access account");
        a.setVerificationStatus(VerificationStatus.VERIFYING);
        var result = runtimeAdapter.verify(a);
        if (result.success()) {
            a.setVerificationStatus(VerificationStatus.VERIFIED);
            a.setVerificationMessage(result.message());
            notificationService.create(a.getUserId(), "success", "Xác minh MT5 thành công", "Tài khoản " + a.getAccountNumber() + " đã được xác minh.");
        } else {
            a.setVerificationStatus(VerificationStatus.FAILED);
            a.setVerificationMessage(result.message());
            notificationService.create(a.getUserId(), "error", "Xác minh MT5 thất bại", "Tài khoản " + a.getAccountNumber() + " xác minh thất bại.");
        }
        processLogService.log(a.getId(), null, "VERIFY", result.success() ? "success" : "failed", result.exitCode(), result.message(), null);
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse submitMyAccount(String email, UUID id) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        if (!a.getUserId().equals(user.getId())) throw new BusinessException("ACCESS_DENIED", "Cannot access account");
        a.setStatus(AccountStatus.PENDING);
        a.setAdminAction(AdminActionState.PENDING_ADMIN);
        a.setSubmittedAt(OffsetDateTime.now());
        notificationService.create(a.getUserId(), "warning", "Cần hành động", "Yêu cầu đang chờ admin xử lý.");
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse stopByUser(String email, UUID id) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BusinessException("ACCESS_DENIED", "User not found"));
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        if (!a.getUserId().equals(user.getId())) throw new BusinessException("ACCESS_DENIED", "Cannot access account");
        return stopInternal(a, "USER", user.getId().toString());
    }

    @Transactional
    public Mt5AccountResponse reconfigureByUser(String email, UUID id, UpdateMt5AccountRequest req) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        lifecyclePolicy.ensureCanModifyConfig(a);
        return updateMyAccount(email, id, req);
    }

    public PagedDataResponse<Mt5AccountResponse> adminListPaged(com.goldtrading.backend.admin.dto.request.AdminMt5AccountsQueryRequest query) {
        Sort sort = resolveSort(query.sortBy(), query.sortOrder());
        var pageable = PageRequest.of(query.page(), query.pageSize(), sort);
        UUID effectiveStrategyId = query.strategyId();
        if (!isBlank(query.strategyCode())) {
            var strategy = strategyRepository.findByCodeIgnoreCase(query.strategyCode()).orElse(null);
            if (strategy == null) {
                return new PagedDataResponse<>(List.of(), query.page(), query.pageSize(), 0, 0);
            }
            if (effectiveStrategyId != null && !effectiveStrategyId.equals(strategy.getId())) {
                return new PagedDataResponse<>(List.of(), query.page(), query.pageSize(), 0, 0);
            }
            effectiveStrategyId = strategy.getId();
        }

        UUID effectiveRiskRuleId = query.riskRuleId();
        if (!isBlank(query.riskRuleCode())) {
            var riskRule = riskRuleRepository.findByCodeIgnoreCase(query.riskRuleCode()).orElse(null);
            if (riskRule == null) {
                return new PagedDataResponse<>(List.of(), query.page(), query.pageSize(), 0, 0);
            }
            if (effectiveRiskRuleId != null && !effectiveRiskRuleId.equals(riskRule.getId())) {
                return new PagedDataResponse<>(List.of(), query.page(), query.pageSize(), 0, 0);
            }
            effectiveRiskRuleId = riskRule.getId();
        }

        Set<UUID> portIdsByStatus = null;
        if (!isBlank(query.portStatus())) {
            PortStatus desiredPortStatus;
            try {
                desiredPortStatus = PortStatus.valueOf(query.portStatus().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return new PagedDataResponse<>(List.of(), query.page(), query.pageSize(), 0, 0);
            }
            portIdsByStatus = portMasterRepository.findByStatus(desiredPortStatus).stream()
                    .map(com.goldtrading.backend.ports.domain.entity.PortMaster::getId)
                    .collect(Collectors.toSet());
            if (portIdsByStatus.isEmpty()) {
                return new PagedDataResponse<>(List.of(), query.page(), query.pageSize(), 0, 0);
            }
        }

        Set<UUID> userIdsBySearch = null;
        if (!isBlank(query.search())) {
            String searchToken = query.search().trim();
            userIdsBySearch = userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(searchToken, searchToken).stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());
        }

        final UUID strategyIdFilter = effectiveStrategyId;
        final UUID riskRuleIdFilter = effectiveRiskRuleId;
        final Set<UUID> portIdsFilter = portIdsByStatus;
        final Set<UUID> searchUserIds = userIdsBySearch;
        final String searchToken = isBlank(query.search()) ? null : query.search().trim().toLowerCase();

        Specification<MT5Account> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (!isBlank(query.status())) {
                try {
                    predicates.add(cb.equal(root.get("status"), AccountStatus.valueOf(query.status().trim().toUpperCase())));
                } catch (IllegalArgumentException ex) {
                    predicates.add(cb.disjunction());
                }
            }

            if (!isBlank(query.verificationStatus())) {
                try {
                    predicates.add(cb.equal(root.get("verificationStatus"), VerificationStatus.valueOf(query.verificationStatus().trim().toUpperCase())));
                } catch (IllegalArgumentException ex) {
                    predicates.add(cb.disjunction());
                }
            }

            if (strategyIdFilter != null) {
                predicates.add(cb.equal(root.get("strategyId"), strategyIdFilter));
            }
            if (riskRuleIdFilter != null) {
                predicates.add(cb.equal(root.get("riskRuleId"), riskRuleIdFilter));
            }
            if (!isBlank(query.timeframe())) {
                predicates.add(cb.like(cb.lower(root.get("timeframe")), "%" + query.timeframe().trim().toLowerCase() + "%"));
            }
            if (!isBlank(query.broker())) {
                predicates.add(cb.like(cb.lower(root.get("broker")), "%" + query.broker().trim().toLowerCase() + "%"));
            }
            if (query.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), query.userId()));
            }
            if (portIdsFilter != null) {
                predicates.add(root.get("assignedPortId").in(portIdsFilter));
            }
            if (searchToken != null) {
                var accountNumberLike = cb.like(cb.lower(root.get("accountNumber")), "%" + searchToken + "%");
                var brokerLike = cb.like(cb.lower(root.get("broker")), "%" + searchToken + "%");
                if (searchUserIds == null || searchUserIds.isEmpty()) {
                    predicates.add(cb.or(accountNumberLike, brokerLike));
                } else {
                    predicates.add(cb.or(accountNumberLike, brokerLike, root.get("userId").in(searchUserIds)));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        var pageData = accountRepository.findAll(spec, pageable);
        List<MT5Account> rows = pageData.getContent();
        Map<UUID, User> users = loadUsers(rows);
        Map<UUID, com.goldtrading.backend.strategies.domain.entity.Strategy> strategies = loadStrategies(rows);
        Map<UUID, com.goldtrading.backend.riskrules.domain.entity.RiskRule> riskRules = loadRiskRules(rows);
        Map<UUID, PortMaster> ports = loadPorts(rows);

        if (searchToken != null) {
            Set<UUID> matchedUserIds = new HashSet<>();
            for (var entry : users.entrySet()) {
                User u = entry.getValue();
                if (containsIgnoreCase(u.getFullName(), searchToken) || containsIgnoreCase(u.getEmail(), searchToken)) {
                    matchedUserIds.add(entry.getKey());
                }
            }
            rows = rows.stream()
                    .filter(a -> containsIgnoreCase(a.getAccountNumber(), searchToken)
                            || containsIgnoreCase(a.getBroker(), searchToken)
                            || matchedUserIds.contains(a.getUserId()))
                    .toList();
        }

        List<Mt5AccountResponse> items = rows.stream()
                .map(a -> toResponse(a, users.get(a.getUserId()), strategies.get(a.getStrategyId()), riskRules.get(a.getRiskRuleId()), ports.get(a.getAssignedPortId())))
                .toList();
        return new PagedDataResponse<>(items, pageData.getNumber(), pageData.getSize(), pageData.getTotalElements(), pageData.getTotalPages());
    }

    public List<Mt5AccountResponse> adminList() {
        var rows = accountRepository.findByDeletedAtIsNull(PageRequest.of(0, 500)).getContent();
        Map<UUID, User> users = loadUsers(rows);
        Map<UUID, com.goldtrading.backend.strategies.domain.entity.Strategy> strategies = loadStrategies(rows);
        Map<UUID, com.goldtrading.backend.riskrules.domain.entity.RiskRule> riskRules = loadRiskRules(rows);
        Map<UUID, PortMaster> ports = loadPorts(rows);
        return rows.stream()
                .map(a -> toResponse(a, users.get(a.getUserId()), strategies.get(a.getStrategyId()), riskRules.get(a.getRiskRuleId()), ports.get(a.getAssignedPortId())))
                .toList();
    }

    public Mt5AccountResponse adminGet(UUID id) {
        MT5Account account = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        User user = userRepository.findById(account.getUserId()).orElse(null);
        var strategy = account.getStrategyId() == null ? null : strategyRepository.findById(account.getStrategyId()).orElse(null);
        var riskRule = account.getRiskRuleId() == null ? null : riskRuleRepository.findById(account.getRiskRuleId()).orElse(null);
        var port = account.getAssignedPortId() == null ? null : portMasterRepository.findById(account.getAssignedPortId()).orElse(null);
        return toResponse(account, user, strategy, riskRule, port);
    }

    @Transactional
    public Mt5AccountResponse adminPatch(UUID id, UpdateMt5AccountRequest req) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        if (req.strategyId() != null) a.setStrategyId(req.strategyId());
        if (req.timeframe() != null) a.setTimeframe(req.timeframe());
        if (req.riskRuleId() != null) a.setRiskRuleId(req.riskRuleId());
        if (req.broker() != null) a.setBroker(req.broker());
        if (req.server() != null) a.setServer(req.server());
        a.setLastConfigUpdatedAt(OffsetDateTime.now());
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse adminAssignPort(UUID id, UUID portId, String actor) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        PortMaster p = portMasterRepository.findById(portId).orElseThrow(() -> new BusinessException("NOT_FOUND", "Port not found"));
        portLifecyclePolicy.ensureCanAssign(p);
        a.setAssignedPortId(portId);
        p.setStatus(PortStatus.OCCUPIED);
        p.setCurrentMt5AccountId(a.getId());
        auditLogService.log("ADMIN", actor, "Admin", "ASSIGN_PORT", "MT5_ACCOUNT", a.getId().toString(), "success", "Port assigned", Map.of("portId", portId));
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse adminStart(UUID id, String actor) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        lifecyclePolicy.ensureCanStart(a);
        if (a.getAssignedPortId() == null) throw new BusinessException("VALIDATION_ERROR", "Port assignment required");
        PortMaster p = portMasterRepository.findById(a.getAssignedPortId()).orElseThrow(() -> new BusinessException("NOT_FOUND", "Port not found"));
        if (p.getStatus() != PortStatus.OCCUPIED || !a.getId().equals(p.getCurrentMt5AccountId())) throw new BusinessException("PORT_OCCUPIED", "Port not assigned correctly");

        var result = runtimeAdapter.start(a);
        if (result.success()) {
            a.setStatus(AccountStatus.PROCESSING);
            a.setAdminAction(AdminActionState.PROCESSING);
            a.setStartedAt(OffsetDateTime.now());
            processLogService.log(a.getId(), p.getId(), "START", "success", result.exitCode(), result.message(), Map.of("strategyId", String.valueOf(a.getStrategyId()), "timeframe", a.getTimeframe()));
            notificationService.create(a.getUserId(), "success", "Bot đã bắt đầu", "Bot cho tài khoản " + a.getAccountNumber() + " đã được kích hoạt.");
            auditLogService.log("ADMIN", actor, "Admin", "START_BOT", "MT5_ACCOUNT", a.getId().toString(), "success", "Started", Map.of("port", p.getCode()));
        } else {
            a.setStatus(AccountStatus.FAILED);
            a.setAdminAction(AdminActionState.PENDING_ADMIN);
            a.setAssignedPortId(null);
            p.setStatus(PortStatus.AVAILABLE);
            p.setCurrentMt5AccountId(null);
            processLogService.log(a.getId(), p.getId(), "START", "failed", result.exitCode(), result.message(), null);
            notificationService.create(a.getUserId(), "error", "Bot start failure", "Không thể khởi động bot cho tài khoản " + a.getAccountNumber());
            auditLogService.log("ADMIN", actor, "Admin", "START_BOT", "MT5_ACCOUNT", a.getId().toString(), "failed", "Start failed", Map.of("port", p.getCode()));
        }
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse adminStop(UUID id, String actor) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        return stopInternal(a, "ADMIN", actor);
    }

    @Transactional
    public Mt5AccountResponse adminReleasePort(UUID id, String actor) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        releasePort(a);
        auditLogService.log("ADMIN", actor, "Admin", "RELEASE_PORT", "MT5_ACCOUNT", a.getId().toString(), "success", "Port released", null);
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse adminReset(UUID id, String actor) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        lifecyclePolicy.ensureCanModifyConfig(a);
        a.setStatus(AccountStatus.PENDING);
        a.setAdminAction(AdminActionState.PENDING_ADMIN);
        auditLogService.log("ADMIN", actor, "Admin", "RESET_BOT", "MT5_ACCOUNT", a.getId().toString(), "success", "Reset to pending", null);
        return toResponse(a);
    }

    @Transactional
    public Mt5AccountResponse adminMarkFailed(UUID id, String actor) {
        MT5Account a = accountRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        a.setStatus(AccountStatus.FAILED);
        a.setAdminAction(AdminActionState.PENDING_ADMIN);
        releasePort(a);
        auditLogService.log("ADMIN", actor, "Admin", "MARK_FAILED", "MT5_ACCOUNT", a.getId().toString(), "success", "Manually marked failed", null);
        return toResponse(a);
    }

    private Mt5AccountResponse stopInternal(MT5Account a, String actorType, String actorId) {
        lifecyclePolicy.ensureCanStop(a);
        var result = runtimeAdapter.stop(a);
        if (!result.success()) throw new BusinessException("INVALID_STATUS_TRANSITION", "Stop failed: " + result.message());
        a.setStatus(AccountStatus.STOPPED);
        a.setStoppedAt(OffsetDateTime.now());
        a.setAdminAction(AdminActionState.PENDING_RECONFIGURE);
        UUID portId = a.getAssignedPortId();
        releasePort(a);
        processLogService.log(a.getId(), portId, "STOP", "success", result.exitCode(), result.message(), null);
        notificationService.create(a.getUserId(), "info", "Bot đã dừng", "Bot cho tài khoản " + a.getAccountNumber() + " đã dừng.");
        auditLogService.log(actorType, actorId, actorType, "STOP_BOT", "MT5_ACCOUNT", a.getId().toString(), "success", "Stopped", null);
        return toResponse(a);
    }

    private void releasePort(MT5Account a) {
        if (a.getAssignedPortId() != null) {
            portMasterRepository.findById(a.getAssignedPortId()).ifPresent(p -> {
                p.setStatus(PortStatus.AVAILABLE);
                p.setCurrentMt5AccountId(null);
            });
            a.setAssignedPortId(null);
        }
    }

    private Mt5AccountResponse toResponse(MT5Account a) {
        return new Mt5AccountResponse(a.getId(), a.getUserId(), a.getAccountNumber(), a.getBroker(), a.getServer(), a.getAccountType(),
                a.getVerificationStatus(), a.getVerificationMessage(), a.getStrategyId(), a.getTimeframe(), a.getRiskRuleId(), a.getStatus(),
                a.getAdminAction(), a.getAssignedPortId(), a.getSubmittedAt(), a.getStartedAt(), a.getStoppedAt(), a.getUpdatedAt(),
                null, null, null, null, null);
    }

    private Mt5AccountResponse toResponse(MT5Account a, User user,
                                          com.goldtrading.backend.strategies.domain.entity.Strategy strategy,
                                          com.goldtrading.backend.riskrules.domain.entity.RiskRule riskRule,
                                          PortMaster port) {
        return new Mt5AccountResponse(a.getId(), a.getUserId(), a.getAccountNumber(), a.getBroker(), a.getServer(), a.getAccountType(),
                a.getVerificationStatus(), a.getVerificationMessage(), a.getStrategyId(), a.getTimeframe(), a.getRiskRuleId(), a.getStatus(),
                a.getAdminAction(), a.getAssignedPortId(), a.getSubmittedAt(), a.getStartedAt(), a.getStoppedAt(), a.getUpdatedAt(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getEmail(),
                strategy == null ? null : strategy.getCode(),
                riskRule == null ? null : riskRule.getCode(),
                port == null ? null : port.getCode());
    }

    private Sort resolveSort(String sortBy, String sortOrder) {
        String normalizedSortBy = switch (sortBy) {
            case "updatedAt", "createdAt", "submittedAt", "startedAt", "stoppedAt", "accountNumber", "broker", "status", "verificationStatus", "timeframe" -> sortBy;
            default -> "updatedAt";
        };
        return "asc".equalsIgnoreCase(sortOrder)
                ? Sort.by(normalizedSortBy).ascending()
                : Sort.by(normalizedSortBy).descending();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsIgnoreCase(String source, String token) {
        return source != null && token != null && source.toLowerCase().contains(token.toLowerCase());
    }

    private Map<UUID, User> loadUsers(List<MT5Account> accounts) {
        var userIds = accounts.stream().map(MT5Account::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left, HashMap::new));
    }

    private Map<UUID, com.goldtrading.backend.strategies.domain.entity.Strategy> loadStrategies(List<MT5Account> accounts) {
        var ids = accounts.stream().map(MT5Account::getStrategyId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return strategyRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(com.goldtrading.backend.strategies.domain.entity.Strategy::getId, Function.identity(), (left, right) -> left, HashMap::new));
    }

    private Map<UUID, com.goldtrading.backend.riskrules.domain.entity.RiskRule> loadRiskRules(List<MT5Account> accounts) {
        var ids = accounts.stream().map(MT5Account::getRiskRuleId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return riskRuleRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(com.goldtrading.backend.riskrules.domain.entity.RiskRule::getId, Function.identity(), (left, right) -> left, HashMap::new));
    }

    private Map<UUID, PortMaster> loadPorts(List<MT5Account> accounts) {
        var ids = accounts.stream().map(MT5Account::getAssignedPortId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return portMasterRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(PortMaster::getId, Function.identity(), (left, right) -> left, HashMap::new));
    }

}
