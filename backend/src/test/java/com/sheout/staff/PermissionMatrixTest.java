package com.sheout.staff;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may do what, printed as a table and compared with the copy committed at
 * src/test/resources/staff/permission-matrix.txt.
 * <p>
 * The point is that a role can never quietly gain a permission. Changing
 * StaffRole makes this fail until the snapshot is regenerated
 * (-Dsheout.update-snapshots=true) and committed with it, so the change shows
 * up in review as a line in a table anyone can read, not as an EnumSet edit.
 */
class PermissionMatrixTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/staff/permission-matrix.txt");

    static String matrix() {
        StringBuilder out = new StringBuilder();
        out.append("# Staff permission matrix: Y = the role holds the permission.\n");
        out.append("# Generated from StaffRole by PermissionMatrixTest. Change both together, in one commit.\n");
        out.append("#\n");
        for (StaffRole role : StaffRole.values()) {
            out.append(String.format("# %-4s %s", abbreviation(role), role.name())).append("\n");
        }
        out.append("\n");
        out.append(String.format("%-28s", "permission"));
        for (StaffRole role : StaffRole.values()) {
            out.append(String.format("%5s", abbreviation(role)));
        }
        out.append("\n");
        for (Permission permission : Permission.values()) {
            out.append(String.format("%-28s", permission.key()));
            for (StaffRole role : StaffRole.values()) {
                out.append(String.format("%5s", role.has(permission) ? "Y" : "."));
            }
            out.append("\n");
        }
        return out.toString();
    }

    private static String abbreviation(StaffRole role) {
        return switch (role) {
            case OWNER -> "OWN";
            case MANAGER -> "MGR";
            case VERIFICATION_AGENT -> "VER";
            case SUPPORT_AGENT -> "SUP";
            case SAFETY_RESPONDER -> "SAF";
            case FINANCE -> "FIN";
            case MARKETPLACE_MODERATOR -> "MKT";
            case AUDITOR -> "AUD";
        };
    }

    @Test
    void theMatrixIsTheReviewedOne() throws IOException {
        String actual = matrix();
        System.out.println(actual);
        if (Boolean.getBoolean("sheout.update-snapshots")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
        }
        assertThat(Files.exists(SNAPSHOT))
                .as("No snapshot yet: run with -Dsheout.update-snapshots=true and commit " + SNAPSHOT)
                .isTrue();
        String expected = Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(actual)
                .as("The role/permission matrix changed. If that was meant, rerun with -Dsheout.update-snapshots=true "
                        + "and commit the new " + SNAPSHOT + " in the same commit as the StaffRole change.")
                .isEqualTo(expected);
    }

    @Test
    void theRulesTheBriefFixes() {
        // Nobody but an owner manages managers or owners, edits configuration or reads the audit log.
        for (StaffRole role : StaffRole.values()) {
            if (role != StaffRole.OWNER) {
                assertThat(role.has(Permission.STAFF_MANAGE_OWNER)).as(role + " manages owners").isFalse();
                assertThat(role.has(Permission.STAFF_MANAGE_MANAGER)).as(role + " manages managers").isFalse();
                assertThat(role.has(Permission.CONFIG_EDIT)).as(role + " edits config").isFalse();
                assertThat(role.has(Permission.AUDIT_VIEW)).as(role + " reads the audit log").isFalse();
                assertThat(role.has(Permission.DATA_EXPORT)).as(role + " exports").isFalse();
            }
        }
        // Moving money takes two permissions no single role but OWNER holds both of.
        assertThat(Arrays.stream(StaffRole.values())
                .filter(r -> r.has(Permission.PAYOUTS_PREPARE) && r.has(Permission.PAYOUTS_APPROVE)))
                .containsExactly(StaffRole.OWNER);
        // Personal documents stay with the people whose job is reviewing them.
        for (StaffRole role : new StaffRole[]{StaffRole.SUPPORT_AGENT, StaffRole.SAFETY_RESPONDER, StaffRole.FINANCE,
                StaffRole.MARKETPLACE_MODERATOR, StaffRole.AUDITOR}) {
            assertThat(role.has(Permission.DOCUMENTS_VIEW)).as(role + " sees documents").isFalse();
        }
        // Verification agents see no money; the auditor changes nothing.
        assertThat(StaffRole.VERIFICATION_AGENT.has(Permission.PAYMENTS_VIEW)).isFalse();
        assertThat(StaffRole.VERIFICATION_AGENT.has(Permission.PAYOUTS_PREPARE)).isFalse();
        assertThat(StaffRole.AUDITOR.permissions()).containsExactlyInAnyOrder(
                Permission.REPORTS_FINANCE, Permission.INVOICES_VIEW, Permission.PAYMENTS_VIEW);
        assertThat(StaffRole.MARKETPLACE_MODERATOR.permissions()).containsExactly(Permission.MARKETPLACE_MODERATE);
    }

    @Test
    void everyPermissionKeyIsUniqueAndHeldBySomebody() {
        assertThat(Arrays.stream(Permission.values()).map(Permission::key).distinct().count())
                .isEqualTo(Permission.values().length);
        for (Permission permission : Permission.values()) {
            assertThat(Permission.fromKey(permission.key())).hasValue(permission);
            assertThat(StaffRole.OWNER.has(permission)).isTrue();
        }
    }
}
