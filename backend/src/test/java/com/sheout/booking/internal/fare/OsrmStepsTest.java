package com.sheout.booking.internal.fare;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OsrmStepsTest {

    /** A trimmed real OSRM answer with steps=true: depart, a left turn onto a named road, arrive. */
    private static final String RESPONSE = """
            {"code":"Ok","routes":[{"distance":1234.5,"duration":180.2,
              "geometry":{"type":"LineString","coordinates":[[78.3800,17.4440],[78.3850,17.4460],[78.3915,17.4483]]},
              "legs":[{"steps":[
                {"distance":400.1,"duration":60.4,"name":"Hitech City Road",
                 "maneuver":{"type":"depart","location":[78.3800,17.4440],"bearing_after":60}},
                {"distance":834.4,"duration":119.8,"name":"Road No. 7",
                 "maneuver":{"type":"turn","modifier":"left","location":[78.3850,17.4460]}},
                {"distance":0,"duration":0,"name":"Road No. 7",
                 "maneuver":{"type":"arrive","location":[78.3915,17.4483]}},
                {"distance":50,"duration":9,"name":"",
                 "maneuver":{"type":"roundabout","modifier":"right","exit":2,"location":[78.3900,17.4470]}}
              ]}]}]}
            """;

    @Test
    void stepsComeBackInOrderAsLatLngWithTheRoadAfterEachTurn() throws Exception {
        // Spring Boot's mapper (the one RestClient uses) ignores fields a record does not name; so does this.
        OsrmRouteProvider.OsrmGeometryResponse response = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(RESPONSE, OsrmRouteProvider.OsrmGeometryResponse.class);

        RoutePath path = OsrmRouteProvider.toRoutePath(response.routes().get(0));

        assertThat(path.points()).hasSize(3);
        assertThat(path.points().get(0).lat()).isEqualTo(17.4440);
        assertThat(path.distanceKm()).isEqualTo(1.2345);
        assertThat(path.steps()).extracting(RoutePath.RouteStep::type).containsExactly("depart", "turn", "arrive", "roundabout");
        RoutePath.RouteStep turn = path.steps().get(1);
        assertThat(turn.modifier()).isEqualTo("left");
        assertThat(turn.name()).isEqualTo("Road No. 7");
        assertThat(turn.lat()).isEqualTo(17.4460);
        assertThat(turn.lng()).isEqualTo(78.3850);
        assertThat(path.steps().get(3).exit()).isEqualTo(2);
        assertThat(path.steps().get(3).name()).isEmpty();
    }
}
