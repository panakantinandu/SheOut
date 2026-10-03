package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.booking.GeoAddress;
import com.sheout.sharedkernel.privacy.Pii;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import com.sheout.users.SavedPlace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What the console sees of personal data, and that nothing else changes. All values are made up. */
class ConsoleMaskingTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new ConsoleMasking());

    record Row(String riderPhone, String pausedByPhone, String claimsPhone, GeoAddress pickup, String drop,
               SavedPlace home, @Pii(Pii.Kind.PAN) String pan, @Pii(Pii.Kind.BANK) String accountNumber, String name) {
    }

    private final Row row = new Row("+910000012345", "Asha (Operations manager)", "1800 000 0000",
            new GeoAddress("Flat 4B, Road No. 12, Banjara Hills, Hyderabad, Telangana 500034", 17.41234, 78.44567),
            "Plot 7, Hitech City Main Road, Madhapur, Hyderabad",
            new SavedPlace("12-3-45, Street 6, Kondapur, Hyderabad", 17.46789, 78.36543),
            "ABCDE1234F", "001234567890", "Priya");

    private static void as(StaffRole role) {
        StaffContext.set(new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, UUID.randomUUID(), "Test", false));
    }

    @AfterEach
    void signOut() {
        StaffContext.clear();
    }

    @Test
    void theConsoleSeesPhonesMaskedAndAddressesAsTheirArea() throws Exception {
        as(StaffRole.OWNER);
        JsonNode out = json.valueToTree(row);
        assertThat(out.get("riderPhone").asText()).isEqualTo("00•••••345");
        assertThat(out.get("pickup").get("label").asText()).isEqualTo("Banjara Hills, Hyderabad");
        assertThat(out.get("drop").asText()).isEqualTo("Madhapur, Hyderabad");
        assertThat(out.get("home").get("label").asText()).isEqualTo("Kondapur, Hyderabad");
        assertThat(out.get("home").get("lat").asDouble()).isEqualTo(17.47);
        assertThat(out.get("pan").asText()).isEqualTo("•••••1234F");
        // Not phones, so not touched: a staff member's name, an insurer's helpline.
        assertThat(out.get("pausedByPhone").asText()).isEqualTo("Asha (Operations manager)");
        assertThat(out.get("claimsPhone").asText()).isEqualTo("1800 000 0000");
        assertThat(out.get("name").asText()).isEqualTo("Priya");
    }

    @Test
    void bankDetailsStayWholeOnlyForTheRolesThatSendPayouts() {
        as(StaffRole.MANAGER);
        assertThat(json.valueToTree(row).get("accountNumber").asText()).isEqualTo("••••7890");
        as(StaffRole.FINANCE);
        assertThat(json.valueToTree(row).get("accountNumber").asText()).isEqualTo("001234567890");
    }

    @Test
    void outsideTheConsoleNothingChanges() {
        JsonNode out = json.valueToTree(row);
        assertThat(out.get("riderPhone").asText()).isEqualTo("+910000012345");
        assertThat(out.get("pickup").get("label").asText()).startsWith("Flat 4B");
        assertThat(out.get("home").get("lat").asDouble()).isEqualTo(17.46789);
        assertThat(out.get("pan").asText()).isEqualTo("ABCDE1234F");
    }
}
