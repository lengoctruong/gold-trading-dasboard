package com.goldtrading.backend.integration;

import com.goldtrading.backend.common.*;
import com.goldtrading.backend.mt5accounts.domain.entity.MT5Account;
import com.goldtrading.backend.users.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminContractsIT extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void dashboardSummaryReturnsTypedStableStructure() throws Exception {
        User admin = createUser(RoleType.ADMIN, "admin-dashboard");
        User owner = createUser(RoleType.USER, "owner-dashboard");
        createAccount(owner, AccountStatus.PENDING, VerificationStatus.UNVERIFIED, AdminActionState.PENDING_ADMIN);
        createPort(PortStatus.AVAILABLE);
        createPort(PortStatus.DISABLED);

        var res = mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header(authHeaderName(), bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();

        var body = json(res.getResponse().getContentAsString());
        assertThat(body.path("success").asBoolean()).isTrue();
        var data = body.path("data");
        assertThat(data.has("totalUsers")).isTrue();
        assertThat(data.has("totalMt5Accounts")).isTrue();
        assertThat(data.has("pendingAccounts")).isTrue();
        assertThat(data.has("processingAccounts")).isTrue();
        assertThat(data.has("stoppedAccounts")).isTrue();
        assertThat(data.has("failedAccounts")).isTrue();
        assertThat(data.has("availablePorts")).isTrue();
        assertThat(data.has("occupiedPorts")).isTrue();
        assertThat(data.has("disabledPorts")).isTrue();
        assertThat(data.has("recentAlertsCount")).isTrue();
    }

    @Test
    void adminMt5AccountsListSupportsPagingFilterSortAndSearch() throws Exception {
        User admin = createUser(RoleType.ADMIN, "admin-list");
        User matchedOwner = createUser(RoleType.USER, "john-target");
        User otherOwner = createUser(RoleType.USER, "amy-other");

        MT5Account a1 = createAccount(matchedOwner, AccountStatus.PENDING, VerificationStatus.VERIFIED, AdminActionState.PENDING_ADMIN);
        a1.setAccountNumber("MT5-001-A");
        a1.setBroker("Exness");
        a1.setTimeframe("M5");
        mt5AccountRepository.save(a1);

        MT5Account a2 = createAccount(otherOwner, AccountStatus.PROCESSING, VerificationStatus.FAILED, AdminActionState.PROCESSING);
        a2.setAccountNumber("MT5-999-Z");
        a2.setBroker("ICMarkets");
        a2.setTimeframe("H1");
        mt5AccountRepository.save(a2);

        var pageRes = mockMvc.perform(get("/api/v1/admin/mt5-accounts")
                        .header(authHeaderName(), bearer(admin))
                        .param("page", "0")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andReturn();
        var pageJson = json(pageRes.getResponse().getContentAsString()).path("data");
        assertThat(pageJson.path("items").isArray()).isTrue();
        assertThat(pageJson.path("items").size()).isEqualTo(1);
        assertThat(pageJson.path("page").asInt()).isEqualTo(0);
        assertThat(pageJson.path("pageSize").asInt()).isEqualTo(1);
        assertThat(pageJson.path("totalItems").asLong()).isGreaterThanOrEqualTo(2L);

        var filterRes = mockMvc.perform(get("/api/v1/admin/mt5-accounts")
                        .header(authHeaderName(), bearer(admin))
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        var filterItems = json(filterRes.getResponse().getContentAsString()).path("data").path("items");
        assertThat(filterItems.size()).isGreaterThanOrEqualTo(1);
        for (var item : filterItems) {
            assertThat(item.path("status").asText()).isEqualTo("PENDING");
        }

        var sortRes = mockMvc.perform(get("/api/v1/admin/mt5-accounts")
                        .header(authHeaderName(), bearer(admin))
                        .param("sortBy", "accountNumber")
                        .param("sortOrder", "asc")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        var sortItems = json(sortRes.getResponse().getContentAsString()).path("data").path("items");
        if (sortItems.size() >= 2) {
            String first = sortItems.get(0).path("accountNumber").asText();
            String second = sortItems.get(1).path("accountNumber").asText();
            assertThat(first.compareTo(second)).isLessThanOrEqualTo(0);
        }

        var searchRes = mockMvc.perform(get("/api/v1/admin/mt5-accounts")
                        .header(authHeaderName(), bearer(admin))
                        .param("search", "john-target")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        var searchItems = json(searchRes.getResponse().getContentAsString()).path("data").path("items");
        assertThat(searchItems.size()).isGreaterThanOrEqualTo(1);
        boolean containsTarget = false;
        for (var item : searchItems) {
            if (item.path("userId").asText().equals(matchedOwner.getId().toString())) {
                containsTarget = true;
            }
        }
        assertThat(containsTarget).isTrue();
    }

    @Test
    void adminLogsAndProcessLogsSupportStructuredFiltersForAccountDetail() throws Exception {
        User admin = createUser(RoleType.ADMIN, "admin-logs");
        User owner = createUser(RoleType.USER, "owner-logs");
        MT5Account account = createAccount(owner, AccountStatus.PENDING, VerificationStatus.VERIFIED, AdminActionState.PENDING_ADMIN);
        var unrelatedAccount = createAccount(owner, AccountStatus.PENDING, VerificationStatus.VERIFIED, AdminActionState.PENDING_ADMIN);

        var accountAudit = new com.goldtrading.backend.auditlogs.domain.entity.AuditLog();
        accountAudit.setId(UUID.randomUUID());
        accountAudit.setActorType("ADMIN");
        accountAudit.setActorId(admin.getId().toString());
        accountAudit.setActorName(admin.getFullName());
        accountAudit.setAction("START_BOT");
        accountAudit.setEntityType("MT5_ACCOUNT");
        accountAudit.setEntityId(account.getId().toString());
        accountAudit.setResult("success");
        accountAudit.setMessage("Start ok");
        accountAudit.setMetadataJson("{}");
        accountAudit.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        auditLogRepository.save(accountAudit);

        var unrelatedAudit = new com.goldtrading.backend.auditlogs.domain.entity.AuditLog();
        unrelatedAudit.setId(UUID.randomUUID());
        unrelatedAudit.setActorType("ADMIN");
        unrelatedAudit.setActorId(admin.getId().toString());
        unrelatedAudit.setActorName(admin.getFullName());
        unrelatedAudit.setAction("START_BOT");
        unrelatedAudit.setEntityType("MT5_ACCOUNT");
        unrelatedAudit.setEntityId(unrelatedAccount.getId().toString());
        unrelatedAudit.setResult("success");
        unrelatedAudit.setMessage("Other account");
        unrelatedAudit.setMetadataJson("{}");
        unrelatedAudit.setCreatedAt(OffsetDateTime.now());
        auditLogRepository.save(unrelatedAudit);

        var accountProcess = new com.goldtrading.backend.processlogs.domain.entity.ProcessLog();
        accountProcess.setId(UUID.randomUUID());
        accountProcess.setMt5AccountId(account.getId());
        accountProcess.setPortId(null);
        accountProcess.setActionType("START");
        accountProcess.setResult("success");
        accountProcess.setExitCode(0);
        accountProcess.setMessage("Start process");
        accountProcess.setConfigSnapshotJson("{}");
        accountProcess.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        processLogRepository.save(accountProcess);

        var unrelatedProcess = new com.goldtrading.backend.processlogs.domain.entity.ProcessLog();
        unrelatedProcess.setId(UUID.randomUUID());
        unrelatedProcess.setMt5AccountId(unrelatedAccount.getId());
        unrelatedProcess.setPortId(null);
        unrelatedProcess.setActionType("START");
        unrelatedProcess.setResult("success");
        unrelatedProcess.setExitCode(0);
        unrelatedProcess.setMessage("Other process");
        unrelatedProcess.setConfigSnapshotJson("{}");
        unrelatedProcess.setCreatedAt(OffsetDateTime.now());
        processLogRepository.save(unrelatedProcess);

        var logsRes = mockMvc.perform(get("/api/v1/admin/logs")
                        .header(authHeaderName(), bearer(admin))
                        .param("entityType", "MT5_ACCOUNT")
                        .param("entityId", account.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        var logsItems = json(logsRes.getResponse().getContentAsString()).path("data").path("items");
        for (var item : logsItems) {
            assertThat(item.path("entityType").asText()).isEqualTo("MT5_ACCOUNT");
            assertThat(item.path("entityId").asText()).isEqualTo(account.getId().toString());
        }

        var processRes = mockMvc.perform(get("/api/v1/admin/process-logs")
                        .header(authHeaderName(), bearer(admin))
                        .param("mt5AccountId", account.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        var processItems = json(processRes.getResponse().getContentAsString()).path("data").path("items");
        for (var item : processItems) {
            assertThat(item.path("mt5AccountId").asText()).isEqualTo(account.getId().toString());
        }
    }

    @Test
    void adminMt5AccountResponseContainsEnrichedFields() throws Exception {
        User admin = createUser(RoleType.ADMIN, "admin-enrich");
        User owner = createUser(RoleType.USER, "owner-enrich");
        var strategy = createStrategy("STRAT");
        var riskRule = createRiskRule("RULE");
        var port = createPort(PortStatus.OCCUPIED);
        MT5Account account = createAccount(owner, AccountStatus.PENDING, VerificationStatus.VERIFIED, AdminActionState.PENDING_ADMIN);
        account.setStrategyId(strategy.getId());
        account.setRiskRuleId(riskRule.getId());
        account.setAssignedPortId(port.getId());
        mt5AccountRepository.save(account);

        var res = mockMvc.perform(get("/api/v1/admin/mt5-accounts")
                        .header(authHeaderName(), bearer(admin))
                        .param("userId", owner.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        var items = json(res.getResponse().getContentAsString()).path("data").path("items");
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        var first = items.get(0);
        assertThat(first.has("userFullName")).isTrue();
        assertThat(first.has("userEmail")).isTrue();
        assertThat(first.has("strategyCode")).isTrue();
        assertThat(first.has("riskRuleCode")).isTrue();
        assertThat(first.has("assignedPortCode")).isTrue();
    }

    @Test
    void adminStrategiesRiskRulesAndPlansSupportServerSideFilters() throws Exception {
        User admin = createUser(RoleType.ADMIN, "admin-server-table");
        createStrategy("AAA");
        createStrategy("BBB");
        createRiskRule("RR-A");
        createRiskRule("RR-B");
        createPlan("P-A", PlanType.SUBSCRIPTION, BillingCycle.MONTHLY, PlanStatus.ACTIVE);
        createPlan("P-B", PlanType.PROFIT_SHARING, BillingCycle.YEARLY, PlanStatus.DISABLED);

        var strategiesRes = mockMvc.perform(get("/api/v1/admin/strategies")
                        .header(authHeaderName(), bearer(admin))
                        .param("search", "AAA")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        var strategyItems = json(strategiesRes.getResponse().getContentAsString()).path("data").path("items");
        assertThat(strategyItems.size()).isGreaterThanOrEqualTo(1);

        var riskRes = mockMvc.perform(get("/api/v1/admin/risk-rules")
                        .header(authHeaderName(), bearer(admin))
                        .param("search", "RR-A")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        var riskItems = json(riskRes.getResponse().getContentAsString()).path("data").path("items");
        assertThat(riskItems.size()).isGreaterThanOrEqualTo(1);

        var plansRes = mockMvc.perform(get("/api/v1/admin/plans")
                        .header(authHeaderName(), bearer(admin))
                        .param("status", "ACTIVE")
                        .param("type", "SUBSCRIPTION")
                        .param("billingCycle", "MONTHLY")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        var planItems = json(plansRes.getResponse().getContentAsString()).path("data").path("items");
        assertThat(planItems.size()).isGreaterThanOrEqualTo(1);
        for (var item : planItems) {
            assertThat(item.path("status").asText()).isEqualTo("ACTIVE");
            assertThat(item.path("type").asText()).isEqualTo("SUBSCRIPTION");
            assertThat(item.path("billingCycle").asText()).isEqualTo("MONTHLY");
        }
    }

    @Test
    void adminUserDetailIncludesPreferredLanguageAndStatusChangesPersist() throws Exception {
        User admin = createUser(RoleType.ADMIN, "admin-user-flow");
        User target = createUser(RoleType.USER, "target-user-flow");
        target.setPreferredLanguage("vi");
        userRepository.save(target);

        var detailRes = mockMvc.perform(get("/api/v1/admin/users/" + target.getId())
                        .header(authHeaderName(), bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();
        var detailData = json(detailRes.getResponse().getContentAsString()).path("data");
        assertThat(detailData.path("preferredLanguage").asText()).isEqualTo("vi");

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/suspend")
                        .header(authHeaderName(), bearer(admin)))
                .andExpect(status().isOk());
        var suspended = userRepository.findById(target.getId()).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo(UserStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/activate")
                        .header(authHeaderName(), bearer(admin)))
                .andExpect(status().isOk());
        var activated = userRepository.findById(target.getId()).orElseThrow();
        assertThat(activated.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
