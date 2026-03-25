package com.goldtrading.backend.admin.controller;

import com.goldtrading.backend.admin.dto.request.*;
import com.goldtrading.backend.admin.dto.response.*;
import com.goldtrading.backend.common.BillingCycle;
import com.goldtrading.backend.common.PlanStatus;
import com.goldtrading.backend.common.PlanType;
import com.goldtrading.backend.common.UserStatus;
import com.goldtrading.backend.common.api.ApiResponse;
import com.goldtrading.backend.common.exception.BusinessException;
import com.goldtrading.backend.dashboard.service.DashboardService;
import com.goldtrading.backend.mt5accounts.dto.AssignPortRequest;
import com.goldtrading.backend.mt5accounts.dto.UpdateMt5AccountRequest;
import com.goldtrading.backend.mt5accounts.service.Mt5AccountService;
import com.goldtrading.backend.plans.domain.entity.Plan;
import com.goldtrading.backend.plans.repository.PlanRepository;
import com.goldtrading.backend.ports.domain.entity.PortMaster;
import com.goldtrading.backend.ports.repository.PortMasterRepository;
import com.goldtrading.backend.processlogs.repository.ProcessLogRepository;
import com.goldtrading.backend.riskrules.domain.entity.RiskRule;
import com.goldtrading.backend.riskrules.repository.RiskRuleRepository;
import com.goldtrading.backend.strategies.domain.entity.Strategy;
import com.goldtrading.backend.strategies.repository.StrategyRepository;
import com.goldtrading.backend.users.domain.entity.User;
import com.goldtrading.backend.users.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository userRepository;
    private final Mt5AccountService mt5AccountService;
    private final StrategyRepository strategyRepository;
    private final RiskRuleRepository riskRuleRepository;
    private final PlanRepository planRepository;
    private final PortMasterRepository portMasterRepository;
    private final com.goldtrading.backend.auditlogs.repository.AuditLogRepository auditLogRepository;
    private final ProcessLogRepository processLogRepository;
    private final DashboardService dashboardService;

    @GetMapping("/users")
    public ApiResponse<?> users(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int pageSize,
                                @RequestParam(defaultValue = "createdAt") String sortBy,
                                @RequestParam(defaultValue = "desc") String sortOrder,
                                @RequestParam(required = false) String search) {
        var pageable = PageRequest.of(page, pageSize, "asc".equalsIgnoreCase(sortOrder) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());
        var data = (search == null || search.isBlank())
                ? userRepository.findAll(pageable).map(this::toUserResponse)
                : userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable).map(this::toUserResponse);
        return ApiResponse.ok(PagedDataResponse.of(data));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<?> user(@PathVariable UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found"));
        return ApiResponse.ok(toUserResponse(user));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<?> patchUser(@PathVariable UUID id, @RequestBody @Valid AdminUserUpdateRequest req) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found"));
        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.phone() != null) u.setPhone(req.phone());
        if (req.address() != null) u.setAddress(req.address());
        if (req.preferredLanguage() != null) u.setPreferredLanguage(req.preferredLanguage());
        return ApiResponse.ok(toUserResponse(userRepository.save(u)));
    }

    @PostMapping("/users/{id}/suspend")
    public ApiResponse<?> suspend(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found"));
        u.setStatus(UserStatus.SUSPENDED);
        return ApiResponse.ok(toUserResponse(userRepository.save(u)));
    }

    @PostMapping("/users/{id}/activate")
    public ApiResponse<?> activate(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found"));
        u.setStatus(UserStatus.ACTIVE);
        return ApiResponse.ok(toUserResponse(userRepository.save(u)));
    }

    @GetMapping("/mt5-accounts")
    public ApiResponse<?> adminAccounts(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int pageSize,
                                        @RequestParam(defaultValue = "updatedAt") String sortBy,
                                        @RequestParam(defaultValue = "desc") String sortOrder,
                                        @RequestParam(required = false) UUID strategyId,
                                        @RequestParam(required = false) String strategyCode,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String verificationStatus,
                                        @RequestParam(required = false) String timeframe,
                                        @RequestParam(required = false) UUID riskRuleId,
                                        @RequestParam(required = false) String riskRuleCode,
                                        @RequestParam(required = false) String broker,
                                        @RequestParam(required = false) String portStatus,
                                        @RequestParam(required = false) UUID userId,
                                        @RequestParam(required = false) String search) {
        var query = new AdminMt5AccountsQueryRequest(
                page, pageSize, sortBy, sortOrder, search, status, verificationStatus,
                strategyId, strategyCode, timeframe, riskRuleId, riskRuleCode, broker, portStatus, userId
        );
        return ApiResponse.ok(mt5AccountService.adminListPaged(query));
    }

    @GetMapping("/mt5-accounts/{id}")
    public ApiResponse<?> adminAccount(@PathVariable UUID id) { return ApiResponse.ok(mt5AccountService.adminGet(id)); }

    @PatchMapping("/mt5-accounts/{id}")
    public ApiResponse<?> adminAccountPatch(@PathVariable UUID id, @RequestBody @Valid UpdateMt5AccountRequest req) { return ApiResponse.ok(mt5AccountService.adminPatch(id, req)); }

    @PostMapping("/mt5-accounts/{id}/assign-port")
    public ApiResponse<?> assignPort(Principal principal, @PathVariable UUID id, @RequestBody @Valid AssignPortRequest req) { return ApiResponse.ok(mt5AccountService.adminAssignPort(id, req.portId(), principal.getName())); }

    @PostMapping("/mt5-accounts/{id}/start")
    public ApiResponse<?> start(Principal principal, @PathVariable UUID id) { return ApiResponse.ok(mt5AccountService.adminStart(id, principal.getName())); }

    @PostMapping("/mt5-accounts/{id}/stop")
    public ApiResponse<?> stop(Principal principal, @PathVariable UUID id) { return ApiResponse.ok(mt5AccountService.adminStop(id, principal.getName())); }

    @PostMapping("/mt5-accounts/{id}/reset")
    public ApiResponse<?> reset(Principal principal, @PathVariable UUID id) { return ApiResponse.ok(mt5AccountService.adminReset(id, principal.getName())); }

    @PostMapping("/mt5-accounts/{id}/release-port")
    public ApiResponse<?> release(Principal principal, @PathVariable UUID id) { return ApiResponse.ok(mt5AccountService.adminReleasePort(id, principal.getName())); }

    @PostMapping("/mt5-accounts/{id}/mark-failed")
    public ApiResponse<?> failed(Principal principal, @PathVariable UUID id) { return ApiResponse.ok(mt5AccountService.adminMarkFailed(id, principal.getName())); }

    @GetMapping("/strategies")
    public ApiResponse<?> strategies(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int pageSize,
                                     @RequestParam(defaultValue = "updatedAt") String sortBy,
                                     @RequestParam(defaultValue = "desc") String sortOrder,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(required = false) Boolean active) {
        var pageable = PageRequest.of(page, pageSize, resolveSort(sortBy, sortOrder,
                List.of("updatedAt", "createdAt", "code", "nameVi", "nameEn", "monthlyPrice", "riskLevel", "active"),
                "updatedAt"));
        Specification<Strategy> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (!isBlank(search)) {
                String token = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), token),
                        cb.like(cb.lower(root.get("nameVi")), token),
                        cb.like(cb.lower(root.get("nameEn")), token),
                        cb.like(cb.lower(root.get("description")), token)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return ApiResponse.ok(PagedDataResponse.of(strategyRepository.findAll(spec, pageable).map(this::toStrategyResponse)));
    }

    @PostMapping("/strategies")
    public ApiResponse<?> createStrategy(@RequestBody @Valid StrategyUpsertRequest req) {
        Strategy s = new Strategy();
        s.setId(UUID.randomUUID());
        mapStrategy(s, req);
        return ApiResponse.ok(toStrategyResponse(strategyRepository.save(s)));
    }

    @PatchMapping("/strategies/{id}")
    public ApiResponse<?> patchStrategy(@PathVariable UUID id, @RequestBody @Valid StrategyUpsertRequest req) {
        Strategy s = strategyRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Strategy not found"));
        mapStrategy(s, req);
        return ApiResponse.ok(toStrategyResponse(strategyRepository.save(s)));
    }

    @GetMapping("/risk-rules")
    public ApiResponse<?> rules(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int pageSize,
                                @RequestParam(defaultValue = "updatedAt") String sortBy,
                                @RequestParam(defaultValue = "desc") String sortOrder,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) Boolean active) {
        var pageable = PageRequest.of(page, pageSize, resolveSort(sortBy, sortOrder,
                List.of("updatedAt", "createdAt", "code", "name", "active"),
                "updatedAt"));
        Specification<RiskRule> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (!isBlank(search)) {
                String token = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), token),
                        cb.like(cb.lower(root.get("name")), token),
                        cb.like(cb.lower(root.get("description")), token),
                        cb.like(cb.lower(root.get("paramsJson")), token)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return ApiResponse.ok(PagedDataResponse.of(riskRuleRepository.findAll(spec, pageable).map(this::toRiskRuleResponse)));
    }

    @PostMapping("/risk-rules")
    public ApiResponse<?> createRule(@RequestBody @Valid RiskRuleUpsertRequest req) {
        RiskRule r = new RiskRule();
        r.setId(UUID.randomUUID());
        mapRiskRule(r, req);
        return ApiResponse.ok(toRiskRuleResponse(riskRuleRepository.save(r)));
    }

    @PatchMapping("/risk-rules/{id}")
    public ApiResponse<?> patchRule(@PathVariable UUID id, @RequestBody @Valid RiskRuleUpsertRequest req) {
        RiskRule r = riskRuleRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Risk rule not found"));
        mapRiskRule(r, req);
        return ApiResponse.ok(toRiskRuleResponse(riskRuleRepository.save(r)));
    }

    @GetMapping("/plans")
    public ApiResponse<?> plans(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int pageSize,
                                @RequestParam(defaultValue = "updatedAt") String sortBy,
                                @RequestParam(defaultValue = "desc") String sortOrder,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String billingCycle) {
        var pageable = PageRequest.of(page, pageSize, resolveSort(sortBy, sortOrder,
                List.of("updatedAt", "createdAt", "code", "name", "price", "profitSharePercent", "status", "type", "billingCycle"),
                "updatedAt"));
        final PlanStatus statusFilter = parseEnum(status, PlanStatus.class);
        final PlanType typeFilter = parseEnum(type, PlanType.class);
        final BillingCycle billingCycleFilter = parseEnum(billingCycle, BillingCycle.class);
        if ((!isBlank(status) && statusFilter == null) || (!isBlank(type) && typeFilter == null) || (!isBlank(billingCycle) && billingCycleFilter == null)) {
            return ApiResponse.ok(new PagedDataResponse<>(List.of(), page, pageSize, 0, 0));
        }
        Specification<Plan> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("type"), typeFilter));
            }
            if (billingCycleFilter != null) {
                predicates.add(cb.equal(root.get("billingCycle"), billingCycleFilter));
            }
            if (!isBlank(search)) {
                String token = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), token),
                        cb.like(cb.lower(root.get("name")), token)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return ApiResponse.ok(PagedDataResponse.of(planRepository.findAll(spec, pageable).map(this::toPlanResponse)));
    }

    @PostMapping("/plans")
    public ApiResponse<?> createPlan(@RequestBody @Valid PlanUpsertRequest req) {
        Plan p = new Plan();
        p.setId(UUID.randomUUID());
        mapPlan(p, req);
        return ApiResponse.ok(toPlanResponse(planRepository.save(p)));
    }

    @PatchMapping("/plans/{id}")
    public ApiResponse<?> patchPlan(@PathVariable UUID id, @RequestBody @Valid PlanUpsertRequest req) {
        Plan p = planRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Plan not found"));
        mapPlan(p, req);
        return ApiResponse.ok(toPlanResponse(planRepository.save(p)));
    }

    @GetMapping("/ports")
    public ApiResponse<?> ports(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(PagedDataResponse.of(portMasterRepository.findAll(PageRequest.of(page, pageSize)).map(this::toPortResponse)));
    }

    @PostMapping("/ports")
    public ApiResponse<?> createPort(@RequestBody @Valid PortUpsertRequest req) {
        PortMaster p = new PortMaster();
        p.setId(UUID.randomUUID());
        mapPort(p, req);
        return ApiResponse.ok(toPortResponse(portMasterRepository.save(p)));
    }

    @PatchMapping("/ports/{id}")
    public ApiResponse<?> patchPort(@PathVariable UUID id, @RequestBody @Valid PortUpsertRequest req) {
        PortMaster p = portMasterRepository.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Port not found"));
        mapPort(p, req);
        return ApiResponse.ok(toPortResponse(portMasterRepository.save(p)));
    }

    @GetMapping("/logs")
    public ApiResponse<?> logs(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int pageSize,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) String entityType,
                               @RequestParam(required = false) String entityId,
                               @RequestParam(required = false) String actorType,
                               @RequestParam(required = false) String result) {
        var pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Specification<com.goldtrading.backend.auditlogs.domain.entity.AuditLog> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!isBlank(search)) {
                String token = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), token),
                        cb.like(cb.lower(root.get("actorName")), token)
                ));
            }
            if (!isBlank(entityType)) {
                predicates.add(cb.equal(cb.lower(root.get("entityType")), entityType.trim().toLowerCase()));
            }
            if (!isBlank(entityId)) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (!isBlank(actorType)) {
                predicates.add(cb.equal(cb.lower(root.get("actorType")), actorType.trim().toLowerCase()));
            }
            if (!isBlank(result)) {
                predicates.add(cb.equal(cb.lower(root.get("result")), result.trim().toLowerCase()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        var data = auditLogRepository.findAll(spec, pageable);
        return ApiResponse.ok(PagedDataResponse.of(data));
    }

    @GetMapping("/process-logs")
    public ApiResponse<?> processLogs(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int pageSize,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(required = false) UUID mt5AccountId,
                                      @RequestParam(required = false) UUID portId,
                                      @RequestParam(required = false) String actionType,
                                      @RequestParam(required = false) String result) {
        var pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Specification<com.goldtrading.backend.processlogs.domain.entity.ProcessLog> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!isBlank(search)) {
                String token = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actionType")), token),
                        cb.like(cb.lower(root.get("message")), token)
                ));
            }
            if (mt5AccountId != null) {
                predicates.add(cb.equal(root.get("mt5AccountId"), mt5AccountId));
            }
            if (portId != null) {
                predicates.add(cb.equal(root.get("portId"), portId));
            }
            if (!isBlank(actionType)) {
                predicates.add(cb.equal(cb.lower(root.get("actionType")), actionType.trim().toLowerCase()));
            }
            if (!isBlank(result)) {
                predicates.add(cb.equal(cb.lower(root.get("result")), result.trim().toLowerCase()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        var data = processLogRepository.findAll(spec, pageable);
        return ApiResponse.ok(PagedDataResponse.of(data));
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<?> dashboard() {
        return ApiResponse.ok(dashboardService.summary());
    }

    private AdminUserResponse toUserResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getAddress(), user.getPreferredLanguage(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }

    private StrategyResponse toStrategyResponse(Strategy s) {
        return new StrategyResponse(s.getId(), s.getCode(), s.getNameVi(), s.getNameEn(), s.getDescription(), s.getMonthlyPrice(), s.getRiskLevel(), s.getSupportedTimeframes(), s.getActive(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private RiskRuleResponse toRiskRuleResponse(RiskRule r) {
        return new RiskRuleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getParamsJson(), r.getActive(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private PlanResponse toPlanResponse(Plan p) {
        return new PlanResponse(p.getId(), p.getCode(), p.getType(), p.getName(), p.getBillingCycle(), p.getPrice(), p.getProfitSharePercent(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private PortResponse toPortResponse(PortMaster p) {
        return new PortResponse(p.getId(), p.getCode(), p.getIpAddress(), p.getPortNumber(), p.getEnvironment(), p.getBrokerBinding(), p.getStatus(), p.getCurrentMt5AccountId(), p.getNote(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private void mapStrategy(Strategy target, StrategyUpsertRequest req) {
        target.setCode(req.code());
        target.setNameVi(req.nameVi());
        target.setNameEn(req.nameEn());
        target.setDescription(req.description());
        target.setMonthlyPrice(req.monthlyPrice());
        target.setRiskLevel(req.riskLevel());
        target.setSupportedTimeframes(req.supportedTimeframes());
        target.setActive(req.active());
    }

    private void mapRiskRule(RiskRule target, RiskRuleUpsertRequest req) {
        target.setCode(req.code());
        target.setName(req.name());
        target.setDescription(req.description());
        target.setParamsJson(req.paramsJson());
        target.setActive(req.active());
    }

    private void mapPlan(Plan target, PlanUpsertRequest req) {
        target.setCode(req.code());
        target.setType(req.type());
        target.setName(req.name());
        target.setBillingCycle(req.billingCycle());
        target.setPrice(req.price());
        target.setProfitSharePercent(req.profitSharePercent());
        target.setStatus(req.status());
    }

    private void mapPort(PortMaster target, PortUpsertRequest req) {
        target.setCode(req.code());
        target.setIpAddress(req.ipAddress());
        target.setPortNumber(req.portNumber());
        target.setEnvironment(req.environment());
        target.setBrokerBinding(req.brokerBinding());
        target.setStatus(req.status());
        target.setNote(req.note());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Sort resolveSort(String sortBy, String sortOrder, List<String> allowedFields, String defaultField) {
        String field = allowedFields.contains(sortBy) ? sortBy : defaultField;
        return "asc".equalsIgnoreCase(sortOrder) ? Sort.by(field).ascending() : Sort.by(field).descending();
    }
}
