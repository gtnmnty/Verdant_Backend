package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.models.enums.AppointmentStatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    // user is a relation → traverse via user.id
    List<Appointment> findByUser_Id(UUID userId);

    // stylist is a relation → traverse via stylist.id
    List<Appointment> findByStylist_Id(UUID stylistId);

    // status is an enum, not a String
    List<Appointment> findByStatus(AppointmentStatusType status);

    // order by scheduledAt (the actual field name), traverse user relation
    List<Appointment> findByUser_IdOrderByScheduledAtDesc(UUID userId);

    // scheduledAt is OffsetDateTime, not LocalDate — use @Query with date cast
    @Query("SELECT a FROM Appointment a WHERE a.stylist.id = :stylistId AND CAST(a.scheduledAt AS date) = :date")
    List<Appointment> findByStylistIdAndDate(@Param("stylistId") UUID stylistId, @Param("date") LocalDate date);
}