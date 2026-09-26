package com.sheout.campaigns;

/** A campaign draft the operator has to fix - the message says what, in words for the console. */
public class CampaignValidationException extends RuntimeException {
    public CampaignValidationException(String message) {
        super(message);
    }
}
