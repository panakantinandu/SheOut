package com.sheout.driververification.internal.checks;

import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckProvidersTest {

    private final ManualCheckProvider manual = new ManualCheckProvider();

    /** A stand-in vendor that only does selfie matching. */
    private final CheckProvider vendor = new CheckProvider() {
        public String name() {
            return "acme";
        }

        public boolean handles(CheckType type) {
            return type == CheckType.SELFIE_MATCH;
        }

        public Verdict check(Request request) {
            return new Verdict(CheckStatus.PASSED, "match", "ref-1");
        }
    };

    @Test
    void everyCheckIsManualUnlessAVendorIsNamedForIt() {
        CheckProviders providers = new CheckProviders(List.of(manual, vendor), "SELFIE_MATCH=acme");
        assertThat(providers.forType(CheckType.SELFIE_MATCH).name()).isEqualTo("acme");
        assertThat(providers.forType(CheckType.LICENCE).name()).isEqualTo("manual");
        assertThat(new CheckProviders(List.of(manual), "").forType(CheckType.IDENTITY).name()).isEqualTo("manual");
    }

    @Test
    void aMistakeInTheSettingStopsStartupRatherThanFallingBackToNobody() {
        assertThatThrownBy(() -> new CheckProviders(List.of(manual), "LICENCE=acme")).hasMessageContaining("unknown provider");
        assertThatThrownBy(() -> new CheckProviders(List.of(manual, vendor), "LICENCE=acme")).hasMessageContaining("does not handle");
        assertThatThrownBy(() -> new CheckProviders(List.of(manual), "LICENSE=manual")).hasMessageContaining("unknown check");
    }

    @Test
    void theManualProviderAsksAPersonAndNeverRejects() {
        for (CheckType type : CheckType.values()) {
            CheckProvider.Verdict verdict = manual.check(new CheckProvider.Request(null, type, null, "v1"));
            assertThat(verdict.status()).isIn(CheckStatus.NEEDS_REVIEW, CheckStatus.PASSED);
            assertThat(verdict.reason()).isNotBlank();
        }
        assertThatThrownBy(() -> new CheckProvider.Verdict(CheckStatus.APPROVED_BY_PERSON, "no", null))
                .as("a provider cannot approve for a person").isInstanceOf(IllegalArgumentException.class);
    }
}
