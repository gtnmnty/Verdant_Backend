package com.verdant.salon_ecomm.models.enums.accounts;

// Delivery state of the last staff-issued reset / set-password link (H-9).
// Mirrored by the ResetLinkStatus enum in graphql/account.graphql (EnumSchemaParityTest keeps them equal).
public enum ResetLinkStatus {
    PENDING, // issued, email not attempted yet
    SENT,    // the mail server accepted it (not proof the inbox received it)
    FAILED   // the send threw; an admin can press "Reset password" again
}
