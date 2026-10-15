package com.sheout.driververification.internal;

import com.sheout.sharedkernel.retention.RetentionTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Verification data that is removed once old enough: start-of-shift photos
 * (sheout.retention.days.shift-photos), the images of rejected partner
 * documents (sheout.retention.days.rejected-documents), and court-record
 * screenings - after a rejection (court-screenings-rejected) and after she
 * leaves (court-screenings-departed). The rules live in
 * the services that own the data; these only name them for the sweeper.
 */
@Configuration
class DocumentRetentionTasks {

    @Bean
    RetentionTask shiftPhotoRetention(ShiftCheckService shiftChecks) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "shift-photos";
            }

            @Override
            public int sweep(Instant cutoff, boolean dryRun, int limit) {
                return shiftChecks.purgePhotosBefore(cutoff, dryRun, limit);
            }
        };
    }

    @Bean
    RetentionTask rejectedDocumentRetention(PartnerDocumentService partnerDocuments) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "rejected-documents";
            }

            @Override
            public int sweep(Instant cutoff, boolean dryRun, int limit) {
                return partnerDocuments.purgeRejectedImagesBefore(cutoff, dryRun, limit);
            }
        };
    }

    @Bean
    RetentionTask courtScreeningRejectedRetention(CourtScreeningService screenings) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "court-screenings-rejected";
            }

            @Override
            public int sweep(Instant cutoff, boolean dryRun, int limit) {
                return screenings.anonymiseRejectedBefore(cutoff, dryRun, limit);
            }
        };
    }

    @Bean
    RetentionTask courtScreeningDepartedRetention(CourtScreeningService screenings) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "court-screenings-departed";
            }

            @Override
            public int sweep(Instant cutoff, boolean dryRun, int limit) {
                return screenings.anonymiseDepartedBefore(cutoff, dryRun, limit);
            }
        };
    }
}
