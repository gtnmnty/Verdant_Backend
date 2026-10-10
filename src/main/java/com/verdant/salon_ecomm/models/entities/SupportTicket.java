package com.verdant.salon_ecomm.models.entities;

import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "support_tickets",
    indexes = {
        @Index(name = "idx_support_ticket_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_support_ticket_status_created", columnList = "status, created_at")
    }
)
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-friendly reference shown in the UI, e.g. "T-2104". Generated from a DB sequence. */
    @Column(name = "ticket_number", nullable = false, unique = true, length = 20)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Staff member handling the ticket; null = unassigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @Column(nullable = false, length = 200)
    private String subject;

    @Builder.Default
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SupportTicketCategory category = SupportTicketCategory.OTHER;

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SupportTicketPriority priority = SupportTicketPriority.NORMAL;

    // Snapshot of the contact form: replies go here even if the account email changes later.
    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC")
    private List<SupportTicketMessage> messages = new ArrayList<>();

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    public void addMessage(SupportTicketMessage message) {
        message.setTicket(this);
        messages.add(message);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
