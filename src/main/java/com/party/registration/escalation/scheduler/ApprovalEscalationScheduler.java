package com.party.registration.escalation.scheduler;

import com.party.registration.escalation.service.ApprovalEscalationBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.registration.approval.escalation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ApprovalEscalationScheduler {
    private final ApprovalEscalationBatchService batchService;

    @Scheduled(fixedDelayString = "${app.registration.approval.escalation.poll-interval-ms:60000}",
            initialDelayString = "${app.registration.approval.escalation.initial-delay-ms:15000}")
    public void scan() {
        try {
            var result = batchService.processBatch();
            if (result.overdueMarked() > 0 || result.escalated() > 0) {
                log.info("Approval escalation scan: {} processed, {} overdue, {} escalated, {} unresolved, {} failed",
                        result.scanned(), result.overdueMarked(), result.escalated(), result.unresolved(), result.failed());
            }
        } catch (RuntimeException exception) {
            log.error("Could not load approval escalation batch; the next scheduled scan will retry", exception);
        }
    }
}
