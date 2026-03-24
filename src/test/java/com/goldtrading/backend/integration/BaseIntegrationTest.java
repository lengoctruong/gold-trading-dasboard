package com.goldtrading.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldtrading.backend.auditlogs.repository.AuditLogRepository;
import com.goldtrading.backend.common.*;
import com.goldtrading.backend.mt5accounts.domain.entity.MT5Account;
import com.goldtrading.backend.mt5accounts.repository.MT5AccountRepository;
import com.goldtrading.backend.notifications.domain.entity.Notification;
import com.goldtrading.backend.notifications.repository.NotificationRepository;
import com.goldtrading.backend.ports.domain.entity.PortMaster;
import com.goldtrading.backend.ports.repository.PortMasterRepository;
import com.goldtrading.backend.processlogs.repository.ProcessLogRepository;
import com.goldtrading.backend.riskrules.domain.entity.RiskRule;
import com.goldtrading.backend.riskrules.repository.RiskRuleRepository;
import com.goldtrading.backend.security.AppUserPrincipal;
import com.goldtrading.backend.security.jwt.JwtService;
import com.goldtrading.backend.strategies.domain.entity.Strategy;
import com.goldtrading.backend.strategies.repository.StrategyRepository;
import com.goldtrading.backend.users.domain.entity.User;
import com.goldtrading.backend.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
public abstract class BaseIntegrationTest {
    private static final String TEST_JWT_SECRET_BASE64 = generateBase64Key(32);
    private static final String TEST_MT5_ENCRYPTION_KEY = generateAsciiKey(32);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("goldtrading")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET_BASE64);
        registry.add("app.mt5.encryption-key", () -> TEST_MT5_ENCRYPTION_KEY);
    }

    private static String generateBase64Key(int sizeBytes) {
        byte[] key = new byte[sizeBytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static String generateAsciiKey(int sizeBytes) {
        byte[] key = new byte[sizeBytes];
        new SecureRandom().nextBytes(key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key).substring(0, sizeBytes);
    }

    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtService jwtService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected MT5AccountRepository mt5AccountRepository;
    @Autowired protected PortMasterRepository portMasterRepository;
    @Autowired protected StrategyRepository strategyRepository;
    @Autowired protected RiskRuleRepository riskRuleRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected AuditLogRepository auditLogRepository;
    @Autowired protected ProcessLogRepository processLogRepository;
    @Autowired protected IntegrationTestConfig.ControlledTestBotRuntimeAdapter controlledAdapter;

    @BeforeEach
    void resetAdapter() {
        controlledAdapter.reset();
    }

    protected User createUser(RoleType role, String prefix) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(prefix + " User");
        user.setEmail(prefix + "+" + UUID.randomUUID() + "@test.local");
        user.setPhone("0900000000");
        user.setAddress("Test Address");
        user.setPasswordHash("$2a$10$qfwuvQTVNsjSlyj1flBz5ONh9S7M2L4nCCjfd5W2kyfkswQ6hQ5x6");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferredLanguage("vi");
        user.setFailedLoginCount(0);
        return userRepository.save(user);
    }

    protected String bearer(User user) {
        String token = jwtService.generateAccessToken(new AppUserPrincipal(user));
        return "Bearer " + token;
    }

    protected Strategy createStrategy(String code) {
        Strategy s = new Strategy();
        s.setId(UUID.randomUUID());
        s.setCode(code + "-" + UUID.randomUUID().toString().substring(0, 6));
        s.setNameVi("Strategy VI");
        s.setNameEn("Strategy EN");
        s.setDescription("d");
        s.setMonthlyPrice(BigDecimal.ZERO);
        s.setRiskLevel("medium");
        s.setSupportedTimeframes("M5,H1");
        s.setActive(true);
        return strategyRepository.save(s);
    }

    protected RiskRule createRiskRule(String code) {
        RiskRule r = new RiskRule();
        r.setId(UUID.randomUUID());
        r.setCode(code + "-" + UUID.randomUUID().toString().substring(0, 6));
        r.setName("Risk");
        r.setDescription("d");
        r.setParamsJson("{\"maxRiskPercent\":2}");
        r.setActive(true);
        return riskRuleRepository.save(r);
    }

    protected PortMaster createPort(PortStatus status) {
        PortMaster p = new PortMaster();
        p.setId(UUID.randomUUID());
        p.setCode("TP-" + UUID.randomUUID().toString().substring(0, 8));
        p.setIpAddress("10.0.0.1");
        p.setPortNumber(9000 + (int) (Math.random() * 2000));
        p.setEnvironment("test");
        p.setBrokerBinding("Exness");
        p.setStatus(status);
        p.setNote("test");
        return portMasterRepository.save(p);
    }

    protected MT5Account createAccount(User owner, AccountStatus status, VerificationStatus verification, AdminActionState adminAction) {
        MT5Account a = new MT5Account();
        a.setId(UUID.randomUUID());
        a.setUserId(owner.getId());
        a.setAccountNumber(String.valueOf(Math.abs(UUID.randomUUID().hashCode())).substring(0, 8));
        a.setEncryptedPassword("enc");
        a.setBroker("Exness");
        a.setServer("Exness-MT5Trial14");
        a.setAccountType(AccountType.REAL);
        a.setVerificationStatus(verification);
        a.setStatus(status);
        a.setAdminAction(adminAction);
        a.setLastConfigUpdatedAt(OffsetDateTime.now());
        return mt5AccountRepository.save(a);
    }

    protected Notification createNotification(User owner) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setUserId(owner.getId());
        n.setType("info");
        n.setTitle("t");
        n.setMessage("m");
        n.setReadAt(null);
        return notificationRepository.save(n);
    }

    protected JsonNode json(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    protected String authHeaderName() {
        return HttpHeaders.AUTHORIZATION;
    }
}

