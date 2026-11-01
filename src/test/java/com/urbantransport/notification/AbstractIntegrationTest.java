package com.urbantransport.notification;

import com.jayway.jsonpath.JsonPath;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.repository.PrivateTripLegRepository;
import com.urbantransport.notification.repository.TripDirectoryRepository;
import com.urbantransport.notification.repository.TripPassengerRepository;
import com.urbantransport.notification.repository.UserDirectoryRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(properties = {
        "notification.dispatch.poll-interval-ms=3600000",
        "notification.license.cron=-"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    protected static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    protected static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected EmailLogRepository emailLogRepository;

    @Autowired
    protected UserDirectoryRepository userDirectoryRepository;

    @Autowired
    protected DriverDirectoryRepository driverDirectoryRepository;

    @Autowired
    protected TripDirectoryRepository tripDirectoryRepository;

    @Autowired
    protected PrivateTripLegRepository privateTripLegRepository;

    @Autowired
    protected TripPassengerRepository tripPassengerRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE email_log, processed_events, user_directory, driver_directory, "
                + "license_alert_sent, trip_directory, private_trip_leg, trip_passenger CASCADE");
    }

    protected static RequestPostProcessor systemAdmin() {
        return userWithRole("SYSTEM_ADMIN", null);
    }

    protected static RequestPostProcessor userWithRole(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject(USER_ID.toString()).claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    protected static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    protected static UUID idAt(MvcResult result, String path) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), path));
    }

    protected EmailLogEntity saveEmail(EmailStatus status, String recipient, String eventType, UUID referenceId,
                                       Instant nextAttemptAt) {
        return emailLogRepository.save(EmailLogEntity.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .referenceId(referenceId)
                .recipient(recipient)
                .template("notice")
                .subject("Asunto de " + eventType)
                .body(recipient == null ? null : "<p>Hola</p>")
                .status(status)
                .attempts(status == EmailStatus.FAILED ? 5 : 0)
                .lastError(status == EmailStatus.FAILED ? "Gmail caido" : null)
                .nextAttemptAt(nextAttemptAt)
                .sentAt(status == EmailStatus.SENT ? Instant.now() : null)
                .build());
    }

    protected void setCreatedAt(UUID emailId, Instant createdAt) {
        jdbcTemplate.update("UPDATE email_log SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), emailId);
    }

    protected UserDirectoryEntity saveUser(Role role, String email, UUID branchId, UUID customerId, boolean active) {
        return userDirectoryRepository.save(UserDirectoryEntity.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .fullName("Usuario " + email)
                .role(role)
                .branchId(branchId)
                .customerId(customerId)
                .active(active)
                .lastEventAt(Instant.now())
                .build());
    }

    protected DriverDirectoryEntity saveDriver(UUID branchId, String email, LocalDate expiresOn, boolean active) {
        return driverDirectoryRepository.save(DriverDirectoryEntity.builder()
                .driverId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .fullName("Chofer " + email)
                .email(email)
                .branchId(branchId)
                .licenseNumber("LIC-" + email.hashCode())
                .licenseExpiresOn(expiresOn)
                .active(active)
                .lastEventAt(Instant.now())
                .build());
    }

    protected List<EmailLogEntity> emailsOfEvent(UUID eventId) {
        return emailLogRepository.findAll().stream().filter(email -> eventId.equals(email.getEventId())).toList();
    }

    protected int count(String sql, Object... args) {
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return total == null ? 0 : total;
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                }
            }));
            return postgres;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
