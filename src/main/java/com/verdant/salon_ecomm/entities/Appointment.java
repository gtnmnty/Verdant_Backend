package com.verdant.salon_ecomm.entities;

import com.verdant.salon_ecomm.models.enums.AppointmentStatusType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private SalonService service;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "service_type", nullable = false, length = 20)
    private String serviceType;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(length = 100)
    private String branch;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "home_address", columnDefinition = "jsonb")
    private Map<String, Object> homeAddress;

    @Column(nullable = false)
    private Short guests = 1;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private AppointmentStatusType status = AppointmentStatusType.PENDING;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }


}
