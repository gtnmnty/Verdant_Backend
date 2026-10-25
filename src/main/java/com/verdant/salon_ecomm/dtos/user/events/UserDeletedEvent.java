package com.verdant.salon_ecomm.dtos.user.events;

import java.util.UUID;

// M-35: a user deleted their own account. Published by UserService.deleteUserById inside its transaction,
// so UserAuditListener (AFTER_COMMIT) records it once the deletion commits. Carries only the id: by the
// time the listener runs, the account is anonymized, and the audit entry shouldn't keep the person's
// name or email either (M-39).
public record UserDeletedEvent(UUID userId) {
}
