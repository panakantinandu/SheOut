package com.sheout.driververification.internal;

import com.sheout.sharedkernel.cluster.ClusterLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Runs the documents' calendar: expires what has run out, sends the 30-,
 * 7- and 1-day reminders, and (see PoliceVerificationService.reverifyDue)
 * puts a police check that has come due back to PENDING.
 * <p>
 * Hourly by default. Dates here are whole days in India, so an hour late is
 * an hour into a day the document was already not valid for at worst - and
 * dispatch never relies on this having run: readiness compares the date
 * itself before every offer. Behind ClusterLock so two servers do not each
 * send every reminder.
 */
@Component
class PartnerDocumentExpirySweeper {

    private final PartnerDocumentService documents;
    private final PoliceVerificationService police;
    private final ClusterLock lock;

    PartnerDocumentExpirySweeper(PartnerDocumentService documents, PoliceVerificationService police, ClusterLock lock) {
        this.documents = documents;
        this.police = police;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${sheout.partner-documents.sweep-interval-ms:3600000}", initialDelay = 90000)
    void sweep() {
        lock.runExclusively("partner-document-expiry", Duration.ofMinutes(10), this::sweepOnce);
    }

    void sweepOnce() {
        documents.expireAndRemind();
        police.reverifyDue();
    }
}
