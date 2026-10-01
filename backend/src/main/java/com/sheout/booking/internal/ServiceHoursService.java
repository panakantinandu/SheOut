package com.sheout.booking.internal;

import com.sheout.booking.ServiceHoursApi;
import com.sheout.booking.ServiceHoursChanged;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reads and changes the single service_hours row. See ServiceHoursApi for
 * what a closed service does and does not stop, and ServiceWindow for the
 * arithmetic.
 * <p>
 * Read from the database on every booking rather than cached. It is one
 * primary-key-sized row, and a pause an operator presses during an incident
 * has to stop the very next booking - a cache that held "open" for another
 * minute would make the button a suggestion.
 */
@Service
public class ServiceHoursService implements ServiceHoursApi {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH);

    private final ServiceHoursRepository repository;
    private final ServiceHoursChangeRepository changes;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Autowired
    public ServiceHoursService(ServiceHoursRepository repository, ServiceHoursChangeRepository changes,
                               DomainEventPublisher events) {
        this(repository, changes, events, Clock.systemUTC());
    }

    ServiceHoursService(ServiceHoursRepository repository, ServiceHoursChangeRepository changes,
                        DomainEventPublisher events, Clock clock) {
        this.repository = repository;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceStatus currentStatus() {
        Instant now = clock.instant();
        ServiceHoursEntity row = row();
        return ServiceWindow.statusAt(now, row.getMode(), row.getOpensAt(), row.getClosesAt(),
                row.pausedAt(now), row.getPauseReason(), row.getPausedUntil());
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceHoursSettings settings() {
        return toSettings(row());
    }

    @Override
    @Transactional
    public ServiceHoursSettings updateSchedule(ServiceHoursMode mode, LocalTime opensAt, LocalTime closesAt, UUID adminAccountId) {
        ServiceHoursEntity row = persistentRow();
        row.schedule(mode, opensAt, closesAt, adminAccountId);
        repository.save(row);
        record(adminAccountId, mode == ServiceHoursMode.ALWAYS_OPEN
                ? "Bookings open at all hours"
                : "Bookings open daily " + clock(opensAt) + " - " + clock(closesAt));
        return toSettings(row);
    }

    @Override
    @Transactional
    public ServiceHoursSettings pause(String reason, Instant until, UUID adminAccountId) {
        ServiceHoursEntity row = persistentRow();
        row.pause(reason, until, clock.instant(), adminAccountId);
        repository.save(row);
        record(adminAccountId, "Paused new bookings"
                + (until == null ? " until resumed" : " until " + UNTIL.format(until.atZone(ServiceWindow.INDIA)))
                + ": " + reason);
        return toSettings(row);
    }

    @Override
    @Transactional
    public ServiceHoursSettings resume(UUID adminAccountId) {
        ServiceHoursEntity row = persistentRow();
        row.resume(adminAccountId);
        repository.save(row);
        record(adminAccountId, "Resumed new bookings");
        return toSettings(row);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceHoursChange> recentChanges(int limit) {
        return changes.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream()
                .map(c -> new ServiceHoursChange(c.getChangedBy(), c.getSummary(), c.getCreatedAt()))
                .toList();
    }

    private void record(UUID by, String summary) {
        changes.save(new ServiceHoursChangeEntity(by, summary));
        events.publish(new ServiceHoursChanged(by, summary));
    }

    private ServiceHoursSettings toSettings(ServiceHoursEntity row) {
        boolean paused = row.pausedAt(clock.instant());
        return new ServiceHoursSettings(row.getMode(), row.getOpensAt(), row.getClosesAt(), paused,
                paused ? row.getPauseReason() : null, paused ? row.getPausedUntil() : null,
                paused ? row.getPausedAt() : null, paused ? row.getPausedBy() : null,
                row.getUpdatedAt(), row.getUpdatedBy());
    }

    /** The row, or always-open defaults when the seed is missing - never a refusal to book. */
    private ServiceHoursEntity row() {
        return repository.findFirstByOrderByCreatedAtAsc().orElseGet(ServiceHoursEntity::alwaysOpen);
    }

    /** The row for a write; created from the defaults on a database the seed never reached. */
    private ServiceHoursEntity persistentRow() {
        return repository.findFirstByOrderByCreatedAtAsc().orElseGet(() -> repository.save(ServiceHoursEntity.alwaysOpen()));
    }

    private static String clock(LocalTime t) {
        return CLOCK.format(t);
    }
}
