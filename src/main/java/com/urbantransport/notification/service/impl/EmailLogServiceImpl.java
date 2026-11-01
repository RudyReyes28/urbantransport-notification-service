package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.dto.EmailLogDetailResponse;
import com.urbantransport.notification.dto.EmailLogSummaryResponse;
import com.urbantransport.notification.dto.EmailStatusCountResponse;
import com.urbantransport.notification.dto.PageResponse;
import com.urbantransport.notification.exception.ApiException;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.repository.EmailStatusCount;
import com.urbantransport.notification.service.EmailLogService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailLogServiceImpl implements EmailLogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    private final EmailLogRepository emailLogRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmailLogSummaryResponse> search(EmailStatus status, String recipient, String eventType,
                                                        UUID referenceId, LocalDate from, LocalDate to,
                                                        int page, int size) {
        Specification<EmailLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (recipient != null && !recipient.isBlank()) {
                String pattern = "%" + escapeLike(recipient.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("recipient")), pattern, '\\'));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (referenceId != null) {
                predicates.add(cb.equal(root.get("referenceId"), referenceId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"),
                        from.atStartOfDay(GUATEMALA).toInstant()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"),
                        to.plusDays(1).atStartOfDay(GUATEMALA).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(emailLogRepository.findAll(spec, pageRequest)
                .map(EmailLogSummaryResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public EmailLogDetailResponse getById(UUID id) {
        return EmailLogDetailResponse.fromEntity(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmailStatusCountResponse> summary() {
        Map<EmailStatus, Long> totals = new EnumMap<>(EmailStatus.class);
        Arrays.stream(EmailStatus.values()).forEach(status -> totals.put(status, 0L));
        for (EmailStatusCount row : emailLogRepository.countByStatus()) {
            totals.put(row.getStatus(), row.getTotal());
        }
        return totals.entrySet().stream()
                .map(entry -> new EmailStatusCountResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public EmailLogSummaryResponse retry(UUID id) {
        if (emailLogRepository.requeue(id, Instant.now(clock)) == 0) {
            EmailLogEntity current = find(id);
            throw new ApiException(HttpStatus.CONFLICT,
                    "Solo se pueden reintentar correos FAILED (estado actual: " + current.getStatus() + ")");
        }
        log.info("Correo {} reencolado manualmente", id);
        return EmailLogSummaryResponse.fromEntity(find(id));
    }

    @Override
    @Transactional
    public int retryAllFailed() {
        int requeued = emailLogRepository.requeueAllFailed(Instant.now(clock));
        log.info("{} correos FAILED reencolados manualmente", requeued);
        return requeued;
    }

    private EmailLogEntity find(UUID id) {
        return emailLogRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Correo no encontrado"));
    }

    /** Evita que % y _ escritos por el usuario actúen como comodines. */
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}