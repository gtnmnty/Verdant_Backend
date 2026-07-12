package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>,
    JpaSpecificationExecutor<Appointment> {

    @Query(value = "SELECT nextval('appointment_code_seq')", nativeQuery = true)
    Long getNextAppointmentCodeSequenceValue();

    @Query("SELECT a FROM Appointment a WHERE a.stylist.id = :stylistId AND CAST(a.scheduledAt AS date) = :date")
    List<Appointment> findByStylistIdAndDate(@Param("stylistId") UUID stylistId, @Param("date") LocalDate date);

    @Query(value = """
        SELECT COUNT(*) > 0 FROM appointments a
        WHERE a.stylist_id = :stylistId
        AND a.status <> 'CANCELLED'
        AND (:excludeId IS NULL OR a.id <> :excludeId)
        AND appointment_time_range(a.scheduled_at, a.duration_minutes)
            && appointment_time_range(CAST(:startTime AS timestamptz), :durationMinutes)
        """, nativeQuery = true)
    boolean existsOverlappingAppointment(
        @Param("stylistId") UUID stylistId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("durationMinutes") Integer durationMinutes,
        @Param("excludeId") UUID excludeId
    );

    // --- myAppointments: RECENT (within 30 days) vs ARCHIVED (outside 30 days) ---

    Page<Appointment> findByUser_IdAndScheduledAtBetween(
        UUID userId, OffsetDateTime windowStart, OffsetDateTime windowEnd, Pageable pageable);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.user.id = :userId
            AND (a.scheduledAt < :windowStart OR a.scheduledAt > :windowEnd)
        """)
    Page<Appointment> findByUserIdOutsideWindow(
        @Param("userId") UUID userId,
        @Param("windowStart") OffsetDateTime windowStart,
        @Param("windowEnd") OffsetDateTime windowEnd,
        Pageable pageable
    );

    // --- appointmentStatusCounts: admin (global) ---

    long countByStatus(AppointmentStatus status);

    long countByStatusInAndScheduledAtAfter(List<AppointmentStatus> statuses, OffsetDateTime after);

    // --- appointmentStatusCounts: client (scoped to the logged-in user) ---

    long countByUser_Id(UUID userId);

    long countByUser_IdAndStatus(UUID userId, AppointmentStatus status);

    long countByUser_IdAndStatusInAndScheduledAtAfter(
        UUID userId, List<AppointmentStatus> statuses, OffsetDateTime after);
}