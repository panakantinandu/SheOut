/**
 * SheOut Help: the in-app help assistant, in both apps.
 * <p>
 * Answers only from SheOut's own content (how the apps work, the Safety
 * Center, and the console-edited FAQ), through Claude. It never tries to
 * handle an emergency: a message that sounds like someone in danger is
 * answered with SOS and 112 before the model is asked anything. What it
 * cannot answer - and every complaint, dispute or payment problem - is handed
 * to a person as a support ticket, which the user sends through the existing
 * Raise an issue form, pre-filled from the conversation.
 * <p>
 * Each account has a daily message cap, and all use is recorded per day for
 * the console's cost view. Public: {@link com.sheout.assistant.AssistantAdminApi}.
 */
package com.sheout.assistant;
