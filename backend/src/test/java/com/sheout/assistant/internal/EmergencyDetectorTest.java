package com.sheout.assistant.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recall first: every distress phrasing here must go to SOS. The ordinary
 * questions must not - a billing question answered with "are you in danger?"
 * teaches people to ignore it.
 */
class EmergencyDetectorTest {

    private final EmergencyDetector detector = new EmergencyDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "help", "Help!!", "please help", "HELP ME", "sos", "emergency",
            "someone help", "I need help now",
            "I'm scared", "i am so scared right now", "I feel unsafe", "I'm in danger",
            "a man is following me", "I think I'm being followed", "the car behind is chasing us",
            "he touched me", "the driver is threatening me", "he won't let me out", "she locked the doors",
            "the driver is not stopping the car", "he has a knife", "call the police", "I'm trapped",
            "bachao", "koi madad karo please", "mujhe dar lag raha hai", "koi peecha kar raha hai",
            "kapadandi", "naaku bhayam ga undi",
            "बचाओ", "मुझे डर लग रहा है", "कोई पीछा कर रहा है",
            "కాపాడండి", "నాకు భయంగా ఉంది", "ఎవరో వెంబడిస్తున్నారు"
    })
    void distressGoesToSos(String message) {
        assertThat(detector.soundsLikeEmergency(message)).as(message).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "help with my payment", "how do I get help with a refund", "I need help changing my phone number",
            "How do I cancel a booking?", "why was I charged twice", "how does driver verification work",
            "what does the SOS button do?", "how do I add emergency contacts", "where is my wallet balance",
            "can I pay with PhonePe", "how do referral rewards work", ""
    })
    void ordinaryQuestionsDoNot(String message) {
        assertThat(detector.soundsLikeEmergency(message)).as(message).isFalse();
    }
}
