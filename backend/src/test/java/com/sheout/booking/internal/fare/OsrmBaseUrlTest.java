package com.sheout.booking.internal.fare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OsrmBaseUrlTest {

    @Test
    void rendersPrivateHostPortBecomesAUrl() {
        assertThat(OsrmRouteProvider.normalise("sheout-osrm:10000")).isEqualTo("http://sheout-osrm:10000");
    }

    @Test
    void aFullUrlIsKeptAndItsTrailingSlashDropped() {
        assertThat(OsrmRouteProvider.normalise("https://router.project-osrm.org/")).isEqualTo("https://router.project-osrm.org");
        assertThat(OsrmRouteProvider.normalise(" http://localhost:5000 ")).isEqualTo("http://localhost:5000");
    }
}
