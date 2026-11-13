package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.numbering.NumberPattern;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SettingsException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Invoice numbers against PostgreSQL: the row lock, the start value and the pattern. Each test uses its own year. */
@SpringBootTest
@Import(TestDatabase.class)
class NumberIT {

    @Autowired
    private NumberService numbers;

    @Autowired
    private TransactionTemplate transactions;

    /** Assigns a number in its own issue transaction, as the issue of an invoice does. */
    private String issue(LocalDate date) {
        return transactions.execute(status -> numbers.assign(date));
    }

    private int counter(String number) {
        return Integer.parseInt(number.substring(number.lastIndexOf('-') + 1));
    }

    @Test
    void parallelIssuesGetEveryNumberOnce() throws Exception {
        LocalDate date = LocalDate.of(2041, 6, 1);
        List<Callable<String>> issues = IntStream.range(0, 20).<Callable<String>>mapToObj(n -> () -> issue(date))
                .toList();
        List<Integer> counters = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<String> issue : pool.invokeAll(issues)) {
                counters.add(counter(issue.get()));
            }
        }
        assertThat(counters).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
    }

    @Test
    void aRolledBackIssueReturnsItsNumber() {
        LocalDate date = LocalDate.of(2042, 1, 2);
        assertThat(issue(date)).isEqualTo(numbers.pattern().format(2042, 1));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            numbers.assign(date);
            throw new IllegalStateException("validation failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(issue(date)).isEqualTo(numbers.pattern().format(2042, 2));
    }

    @Test
    void refusesToAssignANumberOutsideAnIssueTransaction() {
        LocalDate date = LocalDate.of(2045, 1, 1);
        assertThatThrownBy(() -> numbers.assign(date)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(counter(issue(date))).as("the refused call took no number").isEqualTo(1);
    }

    @Test
    void theStartValueIsLockedOnceAYearHasNumbers() {
        numbers.setStart(2043, 12);
        numbers.setStart(2043, 10);
        assertThat(counter(issue(LocalDate.of(2043, 3, 1)))).isEqualTo(10);
        assertThatThrownBy(() -> numbers.setStart(2043, 1)).isInstanceOf(SettingsException.class)
                .hasMessageContaining("Für 2043 sind schon Nummern vergeben.");
        assertThat(counter(issue(LocalDate.of(2043, 12, 31)))).isEqualTo(11);
    }

    @Test
    void storesThePattern() {
        numbers.savePattern("RE-{YY}-{NNN}");
        assertThat(numbers.pattern().value()).isEqualTo("RE-{YY}-{NNN}");
        assertThat(issue(LocalDate.of(2044, 5, 5))).isEqualTo("RE-44-001");
        numbers.savePattern(NumberPattern.DEFAULT.value());
        assertThat(numbers.pattern()).isEqualTo(NumberPattern.DEFAULT);
    }
}
