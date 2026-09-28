package com.sheout.admin.internal.web;

import com.sheout.assistant.AssistantAdminApi;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The console's view of SheOut Help: messages, hand-offs, SOS redirects and estimated cost per day. */
@RestController
@RequestMapping("/api/v1/admin/assistant")
public class AdminAssistantController {

    private final AssistantAdminApi assistant;

    public AdminAssistantController(AssistantAdminApi assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/usage")
    public ResponseEntity<AssistantAdminApi.Usage> usage(@RequestParam(defaultValue = "30") int days) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin only");
        }
        return ResponseEntity.ok(assistant.usage(days));
    }
}
