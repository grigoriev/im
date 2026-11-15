package io.github.grigoriev.im.integrity;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Checks every document and snapshot at startup, every night and on request. It keeps the last result for the overview
 * and writes each new finding into the audit log.
 */
@Service
public class IntegrityMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrityMonitor.class);

    private final List<IntegrityCheck> checks;
    private final AuditService audit;
    private final Clock clock;
    private final AtomicReference<Result> last = new AtomicReference<>();

    /**
     * The result of a check.
     *
     * @param checkedAt when it ran
     * @param damaged what it found changed or broken
     */
    public record Result(Instant checkedAt, List<Damage> damaged) {

        /** Keeps a copy of the findings. */
        public Result {
            damaged = List.copyOf(damaged);
        }

        /**
         * Tells whether all is in order.
         *
         * @return whether the check found nothing
         */
        public boolean intact() {
            return damaged.isEmpty();
        }
    }

    /**
     * Creates the monitor.
     *
     * @param checks the checks of each kind of data
     * @param audit the audit log
     * @param clock the time of a check
     */
    public IntegrityMonitor(List<IntegrityCheck> checks, AuditService audit, Clock clock) {
        this.checks = List.copyOf(checks);
        this.audit = audit;
        this.clock = clock;
    }

    /** Checks once the app is up. */
    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        check();
    }

    /** Checks every night. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Berlin")
    public void nightly() {
        check();
    }

    /**
     * Checks everything now. A finding that the last check did not have goes into the audit log.
     *
     * @return the result
     */
    public synchronized Result check() {
        List<Damage> damaged = checks.stream().flatMap(check -> check.damaged().stream()).toList();
        Result before = last.get();
        Set<String> known = before == null ? Set.of()
                : before.damaged().stream().map(IntegrityMonitor::key).collect(Collectors.toSet());
        for (Damage damage : damaged) {
            if (!known.contains(key(damage))) {
                String summary = damage.summary();
                LOG.warn("Integrity check failed: {}", summary);
                if (damage.invoiceId() == null) {
                    audit.add(AuditAction.INTEGRITY_FAILED, summary);
                } else {
                    audit.add(damage.invoiceId(), AuditAction.INTEGRITY_FAILED, summary);
                }
            }
        }
        Result result = new Result(clock.instant(), damaged);
        last.set(result);
        return result;
    }

    /** Two findings with the same text may concern two objects: the page of each tells them apart. */
    private static String key(Damage damage) {
        return damage.href() + "\n" + damage.summary();
    }

    /**
     * Returns the result of the last check.
     *
     * @return the result, empty before the first check
     */
    public Optional<Result> last() {
        return Optional.ofNullable(last.get());
    }
}
