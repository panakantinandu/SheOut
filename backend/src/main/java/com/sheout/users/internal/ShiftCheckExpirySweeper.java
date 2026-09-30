package com.sheout.users.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A start-of-shift check lasts twelve hours, and going online is where it is
 * asked for. A partner who stays online past it would otherwise work on
 * yesterday's selfie; this takes her offline, and her next Go Online asks
 * for a new one. Dispatch also stops offering her trips the moment the
 * check lapses (DispatchService.isAvailableNow) - this is what makes her
 * own screen agree.
 */
@Component
class ShiftCheckExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ShiftCheckExpirySweeper.class);

    private final DriverProfileService driverProfileService;

    ShiftCheckExpirySweeper(DriverProfileService driverProfileService) {
        this.driverProfileService = driverProfileService;
    }

    @Scheduled(fixedDelayString = "${sheout.shift-check.sweep-interval-ms:300000}", initialDelay = 60000)
    void sweep() {
        int count = driverProfileService.takeOfflineWithoutShiftCheck();
        if (count > 0) {
            log.info("Took {} partner(s) offline: start-of-shift check lapsed", count);
        }
    }
}
