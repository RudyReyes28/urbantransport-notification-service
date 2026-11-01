package com.urbantransport.notification.service;

import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.dto.EmailLogDetailResponse;
import com.urbantransport.notification.dto.EmailLogSummaryResponse;
import com.urbantransport.notification.dto.EmailStatusCountResponse;
import com.urbantransport.notification.dto.PageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmailLogService {

    /** from y to son fechas (hora de Guatemala), ambas inclusivas. Todos los filtros son opcionales. */
    PageResponse<EmailLogSummaryResponse> search(EmailStatus status, String recipient, String eventType,
                                                 UUID referenceId, LocalDate from, LocalDate to,
                                                 int page, int size);

    EmailLogDetailResponse getById(UUID id);

    /** Un conteo por cada estado, incluidos los que están en cero. */
    List<EmailStatusCountResponse> summary();

    /** Solo correos FAILED. 404 si no existe, 409 si está en otro estado. */
    EmailLogSummaryResponse retry(UUID id);

    /** Reencola todos los FAILED; devuelve cuántos. */
    int retryAllFailed();
}