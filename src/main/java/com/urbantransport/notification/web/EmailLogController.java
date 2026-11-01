package com.urbantransport.notification.web;

import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.dto.EmailLogDetailResponse;
import com.urbantransport.notification.dto.EmailLogSummaryResponse;
import com.urbantransport.notification.dto.EmailStatusCountResponse;
import com.urbantransport.notification.dto.PageResponse;
import com.urbantransport.notification.dto.RetryResultResponse;
import com.urbantransport.notification.service.EmailLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/emails")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class EmailLogController {

    private final EmailLogService emailLogService;

    @GetMapping
    public PageResponse<EmailLogSummaryResponse> search(
            @RequestParam(required = false) EmailStatus status,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID referenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return emailLogService.search(status, recipient, eventType, referenceId, from, to, page, size);
    }

    @GetMapping("/summary")
    public List<EmailStatusCountResponse> summary() {
        return emailLogService.summary();
    }

    @GetMapping("/{id}")
    public EmailLogDetailResponse getById(@PathVariable UUID id) {
        return emailLogService.getById(id);
    }

    @PostMapping("/{id}/retry")
    public EmailLogSummaryResponse retry(@PathVariable UUID id) {
        return emailLogService.retry(id);
    }

    @PostMapping("/retry-failed")
    public RetryResultResponse retryFailed() {
        return new RetryResultResponse(emailLogService.retryAllFailed());
    }
}