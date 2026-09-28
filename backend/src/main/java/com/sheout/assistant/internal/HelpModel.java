package com.sheout.assistant.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Optional;

/**
 * The language model behind the help assistant - an interface so the service
 * and its tests never depend on a live API call. ClaudeHelpModel is the one
 * implementation.
 */
interface HelpModel {

    /** False when no API key is configured: the assistant then says it is unavailable and offers a ticket. */
    boolean available();

    /**
     * Answers the conversation from the system prompt's content only. Empty
     * when the call failed or was refused - the caller hands off to a person.
     */
    Optional<Result> answer(String systemPrompt, String languageInstruction, List<Turn> turns);

    record Turn(boolean fromUser, String text) {
    }

    record Result(Decision decision, Usage usage) {
    }

    record Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
    }

    /** What the model decides, as structured output - never free text the app has to interpret. */
    record Decision(
            @JsonPropertyDescription("ANSWER when the SheOut content answers the question. ESCALATE when it does not, when you are not sure, "
                    + "or for any complaint, dispute, refund, payment problem, account problem or report about a person. "
                    + "EMERGENCY if the user may be in danger or distress now.")
            Kind kind,
            @JsonPropertyDescription("The reply shown to the user, in the requested language. Plain text, short, no markdown headings. "
                    + "For ESCALATE: one or two sentences saying a person on SheOut's team will look at it through a support ticket.")
            String reply,
            @JsonPropertyDescription("For ESCALATE only: the best ticket category.")
            TicketCategory ticketCategory,
            @JsonPropertyDescription("For ESCALATE only: a short ticket subject in English, under 80 characters.")
            String ticketSubject,
            @JsonPropertyDescription("For ESCALATE only: a factual summary in English of what the user needs, from the conversation, "
                    + "for the support team. Under 600 characters.")
            String ticketSummary
    ) {
    }

    enum Kind { ANSWER, ESCALATE, EMERGENCY }

    /** The support module's ticket categories, by name. */
    enum TicketCategory { PAYMENT_DISPUTE, DRIVER_OR_CUSTOMER_BEHAVIOR, SAFETY_CONCERN, APP_ISSUE, CANCELLATION_DISPUTE, OTHER }
}
