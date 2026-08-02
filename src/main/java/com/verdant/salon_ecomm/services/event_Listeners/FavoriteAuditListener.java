package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.favorites.events.FavoriteToggledEvent;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FavoriteAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFavoriteToggled(FavoriteToggledEvent event) {
        AuditActionType action = event.added() ? AuditActionType.FAVORITE : AuditActionType.UNFAVORITED;
        String title = (event.added() ?
            "Added to favorites" : "Removed from favorites") + ": " + event.targetName();

        auditLogService.recordSelfService(
            AuditEntityType.FAVORITE,
            event.targetId(),
            action,
            title,
            event.user().getFullName() + " " + (event.added() ? "favorited" : "unfavorited")
                + " " + event.targetType().name().toLowerCase() + " \"" + event.targetName() + "\""
        );
    }
}
