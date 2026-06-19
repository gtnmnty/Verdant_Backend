package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Appointment;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByUserId(UUID userId);
    List<Appointment> findByStylistId(UUID stylistId);
    List<Appointment> findByStatus(String status);
    List<Appointment> findByUserIdOrderByAppointmentDateDesc(UUID userId);
    //@Query("SELECT a FROM Appointment a WHERE a.stylistId = :stylistId AND a.appointmentDate = :date")
    List<Appointment> findByStylistIdAndDate(@Param("stylistId") UUID stylistId, @Param("date") LocalDate date);
}