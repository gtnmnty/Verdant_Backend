package com.verdant.salon_ecomm.entities;

import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "appointments",
    indexes = {
        @Index(name = "idx_appointments_stylist_scheduled", columnList = "stylist_id, scheduled_at"),
        @Index(name = "idx_appointments_user", columnList = "user_id"),
        @Index(name = "idx_appointments_status", columnList = "status")
    }
)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stylist_id")
    private Stylist stylist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private SalonService service;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "service_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AppointmentServiceType serviceType;

    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "home_address", columnDefinition = "jsonb")
    private Map<String, Object> homeAddress;

    @Builder.Default
    @Column(nullable = false)
    private Short guests = 1;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "appointment_code", nullable = false, updatable = false, unique = true)
    private String appointmentCode;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }
}