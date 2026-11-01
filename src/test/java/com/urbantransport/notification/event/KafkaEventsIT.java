package com.urbantransport.notification.event;

import com.urbantransport.notification.AbstractIntegrationTest;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.domain.PrivateTripLegEntity;
import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.TripDirectoryEntity;
import com.urbantransport.notification.domain.TripPassengerEntity;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaEventsIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${notification.kafka.identity-events-topic}")
    private String identityTopic;

    @Value("${notification.kafka.fleet-events-topic}")
    private String fleetTopic;

    @Value("${notification.kafka.ticket-events-topic}")
    private String ticketTopic;

    @Value("${notification.kafka.trip-events-topic}")
    private String tripTopic;

    @Value("${notification.kafka.rental-events-topic}")
    private String rentalTopic;

    @Value("${notification.kafka.payment-events-topic}")
    private String paymentTopic;

    @Test
    void userCreated_isStoredWelcomedOnceAndDuplicateIsIgnored() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID createdEventId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(10);

        send(identityTopic, userId, envelope(createdEventId, EventTypes.USER_CREATED, userId, createdAt,
                user(userId, "Ana@Mail.com", "Ana Lopez", true)));
        send(identityTopic, userId, envelope(createdEventId, EventTypes.USER_CREATED, userId, createdAt,
                user(userId, "Ana@Mail.com", "Duplicado", true)));
        send(identityTopic, userId, envelope(UUID.randomUUID(), EventTypes.USER_UPDATED, userId, Instant.now(),
                user(userId, "ana@mail.com", "Ana Maria Lopez", true)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<UserDirectoryEntity> user = userDirectoryRepository.findById(userId);
            assertTrue(user.isPresent());
            assertEquals("Ana Maria Lopez", user.get().getFullName());
        });
        UserDirectoryEntity user = userDirectoryRepository.findById(userId).orElseThrow();
        assertEquals("ana@mail.com", user.getEmail());
        assertEquals(Role.CUSTOMER, user.getRole());
        assertTrue(user.isActive());
        assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", createdEventId));
        List<EmailLogEntity> welcome = emailsOfEvent(createdEventId);
        assertEquals(1, welcome.size());
        assertEquals("welcome", welcome.getFirst().getTemplate());
        assertEquals("ana@mail.com", welcome.getFirst().getRecipient());
        assertEquals(EmailStatus.PENDING, welcome.getFirst().getStatus());
    }

    @Test
    void driverRegisteredThenDeactivated_updatesDirectory() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        send(fleetTopic, driverId, envelope(UUID.randomUUID(), EventTypes.DRIVER_REGISTERED, driverId,
                Instant.now().minusSeconds(5), driver(driverId, branchId)));
        send(fleetTopic, driverId, envelope(UUID.randomUUID(), EventTypes.DRIVER_DEACTIVATED, driverId,
                Instant.now(), driver(driverId, branchId)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<DriverDirectoryEntity> driver = driverDirectoryRepository.findById(driverId);
            assertTrue(driver.isPresent());
            assertFalse(driver.get().isActive());
        });
        DriverDirectoryEntity driver = driverDirectoryRepository.findById(driverId).orElseThrow();
        assertEquals("juan@mail.com", driver.getEmail());
        assertEquals(branchId, driver.getBranchId());
        assertEquals(today().plusDays(60), driver.getLicenseExpiresOn());
    }

    @Test
    void scheduledTripPurchaseAndChange_notifyDriverBuyerAndPassengers() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DriverDirectoryEntity driver = saveDriver(branchId, "chofer@mail.com", today().plusYears(1), true);
        LocalDateTime departure = today().plusDays(3).atTime(6, 0);
        UUID scheduledEventId = UUID.randomUUID();

        send(tripTopic, tripId, envelope(scheduledEventId, EventTypes.TRIP_SCHEDULED, tripId,
                Instant.now().minusSeconds(20), regularTrip(tripId, driver.getDriverId(), departure)));
        await().atMost(TIMEOUT).untilAsserted(() -> assertTrue(tripDirectoryRepository.findById(tripId).isPresent()));

        UUID purchaseEventId = UUID.randomUUID();
        send(ticketTopic, orderId, envelope(purchaseEventId, EventTypes.TICKET_PURCHASED, orderId,
                Instant.now().minusSeconds(10), purchase(orderId, tripId, departure, "Comprador@Mail.com")));
        await().atMost(TIMEOUT).untilAsserted(() -> assertEquals(2, tripPassengerRepository.findByTripId(tripId).size()));

        UUID updateEventId = UUID.randomUUID();
        send(tripTopic, tripId, envelope(updateEventId, EventTypes.TRIP_UPDATED, tripId, Instant.now(),
                regularTrip(tripId, driver.getDriverId(), departure.plusHours(2))));
        await().atMost(TIMEOUT).untilAsserted(() -> assertEquals(2, emailsOfEvent(updateEventId).size()));

        EmailLogEntity assigned = emailsOfEvent(scheduledEventId).getFirst();
        assertEquals("trip-driver-assigned", assigned.getTemplate());
        assertEquals("chofer@mail.com", assigned.getRecipient());
        EmailLogEntity purchase = emailsOfEvent(purchaseEventId).getFirst();
        assertEquals("ticket-purchased", purchase.getTemplate());
        assertEquals("comprador@mail.com", purchase.getRecipient());
        assertEquals(orderId, purchase.getReferenceId());
        assertTrue(purchase.getBody().contains("1A"));
        List<String> updateTemplates = emailsOfEvent(updateEventId).stream().map(EmailLogEntity::getTemplate)
                .sorted().toList();
        assertEquals(List.of("trip-updated-driver", "trip-updated-passenger"), updateTemplates);
        TripDirectoryEntity trip = tripDirectoryRepository.findById(tripId).orElseThrow();
        assertEquals(departure.plusHours(2), trip.getDepartureAt());
    }

    @Test
    void stalePurchase_isRecordedAsSkipped() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send(ticketTopic, orderId, envelope(eventId, EventTypes.TICKET_PURCHASED, orderId,
                Instant.now().minus(2, ChronoUnit.DAYS),
                purchase(orderId, UUID.randomUUID(), today().plusDays(1).atTime(8, 0), "ana@mail.com")));

        await().atMost(TIMEOUT).untilAsserted(() -> assertEquals(1, emailsOfEvent(eventId).size()));
        EmailLogEntity skipped = emailsOfEvent(eventId).getFirst();
        assertEquals(EmailStatus.SKIPPED, skipped.getStatus());
        assertNull(skipped.getRecipient());
        assertTrue(skipped.getLastError().startsWith("Compra de hace m"));
    }

    @Test
    void cashCancellation_notifiesBranchAdminsAndRemovesPassenger() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        saveUser(Role.BRANCH_ADMIN, "admin1@mail.com", branchId, null, true);
        saveUser(Role.BRANCH_ADMIN, "admin2@mail.com", branchId, null, true);
        saveUser(Role.BRANCH_ADMIN, "otra@mail.com", UUID.randomUUID(), null, true);
        tripPassengerRepository.save(TripPassengerEntity.builder().ticketId(ticketId).tripId(tripId)
                .orderId(orderId).email("ana@mail.com").seatLabel("2A").build());
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", ticketId.toString());
        payload.put("orderId", orderId.toString());
        payload.put("tripId", tripId.toString());
        payload.put("seatNumber", 5);
        payload.put("seatLabel", "2A");
        payload.put("channel", "COUNTER");
        payload.put("branchId", branchId.toString());
        payload.put("customerNit", "CF");
        payload.put("buyerEmail", null);
        payload.put("pricePaid", 45.5);
        payload.put("reason", "TRIP_DELETED");
        payload.put("refundMethod", "CASH");
        payload.put("originName", "Central");
        payload.put("destinationName", "Xela");
        payload.put("departureAt", today().plusDays(2).atTime(6, 0).toString());

        send(ticketTopic, orderId, envelope(eventId, EventTypes.TICKET_CANCELLED, ticketId, Instant.now(), payload));

        await().atMost(TIMEOUT).untilAsserted(() -> assertEquals(2, emailsOfEvent(eventId).size()));
        assertEquals(List.of("admin1@mail.com", "admin2@mail.com"), emailsOfEvent(eventId).stream()
                .map(EmailLogEntity::getRecipient).sorted().toList());
        assertTrue(emailsOfEvent(eventId).stream().allMatch(email -> email.getTemplate().equals("ticket-cancelled-cash")));
        assertFalse(tripPassengerRepository.findById(ticketId).isPresent());
    }

    @Test
    void walletRecharged_sendsReceiptToKnownCustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        saveUser(Role.CUSTOMER, "cliente@mail.com", null, customerId, true);
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customerId.toString());
        payload.put("amount", 250);
        payload.put("paymentDate", today().toString());

        send(paymentTopic, customerId, envelope(eventId, EventTypes.WALLET_RECHARGED, customerId, Instant.now(),
                payload));

        await().atMost(TIMEOUT).untilAsserted(() -> assertEquals(1, emailsOfEvent(eventId).size()));
        EmailLogEntity receipt = emailsOfEvent(eventId).getFirst();
        assertEquals("wallet-recharged", receipt.getTemplate());
        assertEquals("cliente@mail.com", receipt.getRecipient());
        assertEquals("Recarga recibida: Q 250.00", receipt.getSubject());
        assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", eventId));
    }

    @Test
    void rentalRequestedOnline_notifiesAdminsAndCustomer() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        UserDirectoryEntity customer = saveUser(Role.CUSTOMER, "cliente@mail.com", null, UUID.randomUUID(), true);
        saveUser(Role.BRANCH_ADMIN, "admin@mail.com", branchId, null, true);
        UUID eventId = UUID.randomUUID();

        send(rentalTopic, rentalId, envelope(eventId, EventTypes.RENTAL_REQUESTED, rentalId, Instant.now(),
                rental(rentalId, branchId, customer.getUserId())));

        await().atMost(TIMEOUT).untilAsserted(() -> assertEquals(2, emailsOfEvent(eventId).size()));
        Map<String, String> recipients = new LinkedHashMap<>();
        emailsOfEvent(eventId).forEach(email -> recipients.put(email.getTemplate(), email.getRecipient()));
        assertEquals("admin@mail.com", recipients.get("rental-requested-admin"));
        assertEquals("cliente@mail.com", recipients.get("rental-requested-customer"));
    }

    @Test
    void rentalAssignedThenTripScheduled_linksLegAndNotifiesCustomerAndDriver() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UserDirectoryEntity customer = saveUser(Role.CUSTOMER, "cliente@mail.com", null, UUID.randomUUID(), true);
        DriverDirectoryEntity driver = saveDriver(branchId, "chofer@mail.com", today().plusYears(1), true);
        LocalDateTime departure = today().plusDays(6).atTime(9, 30);

        send(rentalTopic, rentalId, envelope(UUID.randomUUID(), EventTypes.RENTAL_ASSIGNED, rentalId, Instant.now(),
                assignment(rentalId, branchId, busId, driver.getDriverId(), customer.getUserId(), departure)));
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(1, privateTripLegRepository.findByRentalIdOrderByLegNoAsc(rentalId).size()));

        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_SCHEDULED, tripId, Instant.now(),
                privateTrip(tripId, busId, driver.getDriverId(), departure)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            PrivateTripLegEntity leg = privateTripLegRepository.findByRentalIdOrderByLegNoAsc(rentalId).getFirst();
            assertEquals(tripId, leg.getTripId());
            assertTrue(leg.getNotifiedAt() != null);
        });
        assertEquals(rentalId, tripDirectoryRepository.findById(tripId).orElseThrow().getRentalId());
        List<EmailLogEntity> emails = emailLogRepository.findAll().stream()
                .filter(email -> rentalId.equals(email.getReferenceId())).toList();
        Map<String, String> recipients = new LinkedHashMap<>();
        emails.forEach(email -> recipients.put(email.getTemplate(), email.getRecipient()));
        assertEquals("cliente@mail.com", recipients.get("private-trip-customer"));
        assertEquals("chofer@mail.com", recipients.get("private-trip-driver"));
        assertEquals(2, emails.size());
    }

    @Test
    void privateTripRejectedAfterAssignment_notifiesAdminsAndDropsLegs() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        saveUser(Role.BRANCH_ADMIN, "admin@mail.com", branchId, null, true);
        DriverDirectoryEntity driver = saveDriver(branchId, "chofer@mail.com", today().plusYears(1), true);

        send(rentalTopic, rentalId, envelope(UUID.randomUUID(), EventTypes.RENTAL_ASSIGNED, rentalId, Instant.now(),
                assignment(rentalId, branchId, busId, driver.getDriverId(), null, today().plusDays(6).atTime(9, 0))));
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(1, privateTripLegRepository.findByRentalIdOrderByLegNoAsc(rentalId).size()));

        UUID eventId = UUID.randomUUID();
        send(tripTopic, rentalId, envelope(eventId, EventTypes.PRIVATE_TRIP_REJECTED, rentalId, Instant.now(),
                Map.of("rentalId", rentalId.toString(), "busId", busId.toString(),
                        "driverId", driver.getDriverId().toString(), "code", "BUS_OVERLAP",
                        "reason", "El bus ya tiene un viaje")));

        await().atMost(TIMEOUT).untilAsserted(() -> assertEquals(1, emailsOfEvent(eventId).size()));
        EmailLogEntity mail = emailsOfEvent(eventId).getFirst();
        assertEquals("private-trip-rejected-admin", mail.getTemplate());
        assertEquals("admin@mail.com", mail.getRecipient());
        assertTrue(privateTripLegRepository.findByRentalIdOrderByLegNoAsc(rentalId).isEmpty());
    }

    @Test
    void malformedTicketEvent_endsInDeadLetterTopic() throws Exception {
        UUID key = UUID.randomUUID();

        send(ticketTopic, key, "{no es json");

        ConsumerRecord<String, String> record = awaitRecord(ticketTopic + ".DLT", key.toString());
        assertEquals("{no es json", record.value());
    }

    @Test
    void incompleteUserEvent_endsInDeadLetterTopicAndSavesNothing() throws Exception {
        UUID userId = UUID.randomUUID();
        Map<String, Object> payload = user(userId, null, "Sin correo", true);
        String message = envelope(UUID.randomUUID(), EventTypes.USER_UPDATED, userId, Instant.now(), payload);

        send(identityTopic, userId, message);

        ConsumerRecord<String, String> record = awaitRecord(identityTopic + ".DLT", userId.toString());
        assertEquals(message, record.value());
        assertFalse(userDirectoryRepository.findById(userId).isPresent());
    }

    private void send(String topic, UUID key, String value) throws Exception {
        kafkaTemplate.send(topic, key.toString(), value).get(10, TimeUnit.SECONDS);
    }

    private String envelope(UUID eventId, String eventType, UUID aggregateId, Instant occurredAt,
                            Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("version", 1);
        envelope.put("correlationId", UUID.randomUUID().toString());
        envelope.put("payload", payload);
        return jsonMapper.writeValueAsString(envelope);
    }

    private static Map<String, Object> user(UUID userId, String email, String fullName, boolean active) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId.toString());
        payload.put("email", email);
        payload.put("fullName", fullName);
        payload.put("role", "CUSTOMER");
        payload.put("branchId", null);
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("active", active);
        return payload;
    }

    private static Map<String, Object> driver(UUID driverId, UUID branchId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("driverId", driverId.toString());
        payload.put("userId", UUID.randomUUID().toString());
        payload.put("fullName", "Juan Perez");
        payload.put("licenseNumber", "LIC-001");
        payload.put("licenseType", "A");
        payload.put("licenseExpiresOn", today().plusDays(60).toString());
        payload.put("phone", "55551234");
        payload.put("email", " Juan@Mail.com ");
        payload.put("baseSalaryPerTrip", 150);
        payload.put("branchId", branchId.toString());
        payload.put("active", true);
        return payload;
    }

    private static Map<String, Object> regularTrip(UUID tripId, UUID driverId, LocalDateTime departure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", tripId.toString());
        payload.put("type", "REGULAR");
        payload.put("originName", "Central");
        payload.put("destinationName", "Xela");
        payload.put("busId", UUID.randomUUID().toString());
        payload.put("busPlate", "P-001");
        payload.put("driverId", driverId.toString());
        payload.put("departureAt", departure.toString());
        payload.put("estimatedArrival", departure.plusHours(4).toString());
        payload.put("status", "SCHEDULED");
        return payload;
    }

    private static Map<String, Object> privateTrip(UUID tripId, UUID busId, UUID driverId, LocalDateTime departure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", tripId.toString());
        payload.put("type", "PRIVATE");
        payload.put("originName", null);
        payload.put("destinationName", null);
        payload.put("originText", "Hotel Antigua");
        payload.put("destinationText", "Lago Atitlan");
        payload.put("busId", busId.toString());
        payload.put("busPlate", "P-777");
        payload.put("driverId", driverId.toString());
        payload.put("departureAt", departure.toString());
        payload.put("estimatedArrival", departure.plusHours(3).toString());
        payload.put("status", "SCHEDULED");
        return payload;
    }

    private static Map<String, Object> purchase(UUID orderId, UUID tripId, LocalDateTime departure, String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("channel", "ONLINE");
        payload.put("branchId", null);
        payload.put("customerNit", "1234567");
        payload.put("buyerEmail", email);
        payload.put("paymentDate", today().toString());
        payload.put("total", 90);
        payload.put("trip", Map.of("tripId", tripId.toString(), "originName", "Central", "destinationName", "Xela",
                "departureAt", departure.toString(), "busPlate", "P-001"));
        payload.put("tickets", List.of(
                Map.of("ticketId", UUID.randomUUID().toString(), "seatNumber", 1, "seatLabel", "1A", "pricePaid", 45),
                Map.of("ticketId", UUID.randomUUID().toString(), "seatNumber", 2, "seatLabel", "1B", "pricePaid", 45)));
        return payload;
    }

    private static Map<String, Object> rental(UUID rentalId, UUID branchId, UUID customerUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rentalId", rentalId.toString());
        payload.put("channel", "ONLINE");
        payload.put("branchId", branchId.toString());
        payload.put("customerNit", "1234567");
        payload.put("customerUserId", customerUserId.toString());
        payload.put("originText", "Central");
        payload.put("destinationText", "Xela");
        payload.put("departureDate", today().plusDays(10).toString());
        payload.put("returnDate", null);
        payload.put("passengers", 30);
        payload.put("distanceKm", 120.5);
        payload.put("estimatedPrice", 1200);
        payload.put("status", "REQUESTED");
        return payload;
    }

    private static Map<String, Object> assignment(UUID rentalId, UUID branchId, UUID busId, UUID driverId,
                                                  UUID customerUserId, LocalDateTime departure) {
        Instant departureAt = departure.atZone(ZONE).toInstant();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rentalId", rentalId.toString());
        payload.put("branchId", branchId.toString());
        payload.put("busId", busId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("passengers", 30);
        payload.put("distanceKm", 80);
        payload.put("customerNit", "1234567");
        payload.put("customerUserId", customerUserId == null ? null : customerUserId.toString());
        payload.put("assignedBy", UUID.randomUUID().toString());
        payload.put("legs", List.of(Map.of("originText", "Hotel Antigua", "destinationText", "Lago Atitlan",
                "departureAt", departureAt.toString(),
                "estimatedArrival", departureAt.plus(3, ChronoUnit.HOURS).toString())));
        return payload;
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("it-" + UUID.randomUUID(), "it")) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(60)).pollInSameThread().untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertTrue(received.stream().anyMatch(record -> key.equals(record.key())),
                        "no llegó el mensaje con key " + key + " a " + topic);
            });
        }
        return received.stream().filter(record -> key.equals(record.key())).findFirst().orElseThrow();
    }
}
