package com.verdant.salon_ecomm.models.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "support_ticket_messages",
    indexes = {
        @Index(name = "idx_support_msg_ticket_created", columnList = "ticket_id, created_at")
    }
)
public class SupportTicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** True when written by staff. Stored (not derived from role) so history survives role changes. */
    @Column(name = "from_staff", nullable = false)
    private boolean fromStaff;

    /** Staff-only note. Must NEVER be returned to the ticket's customer. */
    @Column(name = "is_internal", nullable = false)
    private boolean internal;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
