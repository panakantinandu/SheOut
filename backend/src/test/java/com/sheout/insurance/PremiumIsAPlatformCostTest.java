package com.sheout.insurance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The premium is SheOut's cost, recorded on the coverage row - and that is
 * the only place it lives. Proved by what the code can reach: the insurance
 * module imports nothing from payments, payouts or booking's internals (so
 * it cannot change a fare, a payout or the commission), and neither fares
 * nor payments import insurance (so they cannot add it to a price or take it
 * from her share). If either ever changes, this fails and somebody has to
 * decide that on purpose.
 */
class PremiumIsAPlatformCostTest {

    private static final Path MAIN = Path.of("src/main/java/com/sheout");

    @Test
    void insuranceCannotReachFaresPaymentsOrPayouts() throws IOException {
        assertThat(importsIn(MAIN.resolve("insurance")))
                .noneMatch(i -> i.startsWith("import com.sheout.payments") || i.startsWith("import com.sheout.payouts")
                        || i.startsWith("import com.sheout.booking.internal") || i.contains(".internal.") && !i.contains("com.sheout.insurance"));
    }

    @Test
    void faresPaymentsAndPayoutsDoNotKnowInsuranceExists() throws IOException {
        for (String module : List.of("payments", "payouts", "booking/internal/fare")) {
            assertThat(importsIn(MAIN.resolve(module))).as(module)
                    .noneMatch(i -> i.contains("com.sheout.insurance"));
        }
    }

    private static List<String> importsIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).flatMap(p -> {
                try {
                    return Files.readAllLines(p).stream().filter(l -> l.startsWith("import ")
                            || l.contains("com.sheout.insurance") || l.contains("com.sheout.payments"));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        }
    }
}
