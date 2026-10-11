package com.verdant.salon_ecomm.models.enums.newsletter;

public enum NewsletterCampaignStatus {
    DRAFT,     // editable, nothing sent yet
    SENDING,   // delivery in progress (or interrupted - sending again resumes it)
    SENT       // every subscriber has been attempted; failures (if any) can be retried by sending again
}
