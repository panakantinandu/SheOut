package com.sheout.assistant.internal;

import com.sheout.auth.AccountRole;
import com.sheout.content.ContentApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    private final HelpModel model = mock(HelpModel.class);
    private final AssistantUsageRepository usage = mock(AssistantUsageRepository.class);
    private final ContentApi content = mock(ContentApi.class);
    private final UUID rider = UUID.randomUUID();
    private AssistantService service;

    @BeforeEach
    void setUp() {
        when(content.getContentByPrefix(anyString())).thenReturn(List.of());
        when(model.available()).thenReturn(true);
        service = new AssistantService(new EmergencyDetector(), new KnowledgeBase(content), model, usage, "claude-opus-5", 30, 3000,
                new BigDecimal("5"), new BigDecimal("25"), new BigDecimal("0.5"), new BigDecimal("6.25"));
    }

    private AssistantService.Reply ask(String text) {
        return service.ask(rider, AccountRole.CUSTOMER, "en", List.of(new AssistantService.Turn(true, text)));
    }

    private void modelDecides(HelpModel.Decision decision) {
        when(model.answer(anyString(), anyString(), any()))
                .thenReturn(Optional.of(new HelpModel.Result(decision, new HelpModel.Usage(120, 80, 3000, 0))));
    }

    @Test
    void distressNeverReachesTheModelAndIsSentToSos() {
        AssistantService.Reply reply = ask("someone is following me");

        assertThat(reply.kind()).isEqualTo(AssistantService.Kind.EMERGENCY);
        verify(model, never()).answer(anyString(), anyString(), any());
        verify(usage).add(eq(rider), any(), eq(0), anyLong(), anyLong(), anyLong(), anyLong(), eq(0), eq(1));
    }

    @Test
    void distressIsSentToSosEvenOverTheCapAndWithoutAKey() {
        when(model.available()).thenReturn(false);
        when(usage.messagesOn(eq(rider), any())).thenReturn(99L);

        assertThat(ask("bachao").kind()).isEqualTo(AssistantService.Kind.EMERGENCY);
    }

    @Test
    void aGroundedAnswerIsReturnedAndItsTokensRecorded() {
        modelDecides(new HelpModel.Decision(HelpModel.Kind.ANSWER, "Open the trip and tap Cancel.", null, null, null));

        AssistantService.Reply reply = ask("How do I cancel?");

        assertThat(reply.kind()).isEqualTo(AssistantService.Kind.ANSWER);
        assertThat(reply.text()).isEqualTo("Open the trip and tap Cancel.");
        verify(usage).add(eq(rider), any(), eq(1), eq(120L), eq(80L), eq(3000L), eq(0L), eq(0), eq(0));
    }

    @Test
    void aDisputeBecomesAPrefilledTicketForAPerson() {
        modelDecides(new HelpModel.Decision(HelpModel.Kind.ESCALATE, "A person will look at this.",
                HelpModel.TicketCategory.PAYMENT_DISPUTE, "Charged twice for a trip", "Rider says she was charged twice for one trip."));

        AssistantService.Reply reply = ask("I was charged twice");

        assertThat(reply.kind()).isEqualTo(AssistantService.Kind.ESCALATE);
        assertThat(reply.ticket().category()).isEqualTo("PAYMENT_DISPUTE");
        assertThat(reply.ticket().subject()).isEqualTo("Charged twice for a trip");
        verify(usage).add(eq(rider), any(), eq(1), anyLong(), anyLong(), anyLong(), anyLong(), eq(1), eq(0));
    }

    @Test
    void theModelSpottingDistressTheKeywordsMissedAlsoGoesToSos() {
        modelDecides(new HelpModel.Decision(HelpModel.Kind.EMERGENCY, "Please use SOS now.", null, null, null));

        assertThat(ask("the driver keeps asking where I live and turned off the map").kind())
                .isEqualTo(AssistantService.Kind.EMERGENCY);
    }

    @Test
    void aFailedCallHandsOffToAPersonRatherThanLeavingHerStuck() {
        when(model.answer(anyString(), anyString(), any())).thenReturn(Optional.empty());

        AssistantService.Reply reply = ask("How do payouts work?");

        assertThat(reply.kind()).isEqualTo(AssistantService.Kind.ESCALATE);
        assertThat(reply.ticket().summary()).isEqualTo("How do payouts work?");
    }

    @Test
    void theDailyCapStopsTheModelButOffersATicket() {
        when(usage.messagesOn(eq(rider), any(LocalDate.class))).thenReturn(30L);

        AssistantService.Reply reply = ask("How do payouts work?");

        assertThat(reply.kind()).isEqualTo(AssistantService.Kind.LIMIT_REACHED);
        assertThat(reply.ticket()).isNotNull();
        verify(model, never()).answer(anyString(), anyString(), any());
        verify(usage, never()).add(any(), any(), anyInt(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void theSystemPromptHoldsTheRulesAndOnlySheOutContent() {
        String prompt = new KnowledgeBase(content).systemPrompt(AccountRole.CUSTOMER);

        assertThat(prompt).contains("Answer ONLY from the SheOut content below")
                .contains("EMERGENCY")
                .contains("Discreet SOS")
                .contains("It is a snapshot");
    }
}
