package com.verdant.salon_ecomm.services.events.listeners.appointment;

import com.verdant.salon_ecomm.dtos.appointment.AppointmentEmailContent;
import com.verdant.salon_ecomm.dtos.appointment.AppointmentEmailContent.Detail;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentApprovedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentBookedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentCancelledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentRescheduledEvent;
import com.verdant.salon_ecomm.models.entities.Appointment;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import com.verdant.salon_ecomm.repositories.BranchRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Emails the customer about their appointment: booked, confirmed, rescheduled, cancelled. Same rules as
// OrderEmailListener and GiftCardEmailListener: after the DB commit, off the request thread (@Async), failures
// only logged with the appointment id (never the address). The session is closed by then, so the customer and
// branch are looked up by id instead of through the entity's lazy relations. No @Retryable on purpose: a retry
// after a half-sent mail would email the customer twice.
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentEmailListener {

    // The offset is printed so the time is unambiguous whatever zone the server runs in.
    private static final DateTimeFormatter WHEN_FORMAT =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a (xxx)", Locale.ENGLISH);

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        Appointment appointment = event.appointment();
        send(appointment, "Appointment request received", new Body(
            "We've received your appointment request",
            List.of("Thank you for booking with us. Our concierge will review your request and confirm it shortly. "
                + "We'll email you again once it's confirmed."),
            "View appointment", emailService.appointmentsUrl()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentApproved(AppointmentApprovedEvent event) {
        send(event.appointment(), "Appointment confirmed", new Body(
            "Your appointment is confirmed",
            List.of("Good news: your appointment has been confirmed. We look forward to seeing you."),
            "View appointment", emailService.appointmentsUrl()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentRescheduled(AppointmentRescheduledEvent event) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(event.isSelfService()
            ? "Your appointment has been moved to the new time below."
            : "Our team has moved your appointment to the new time below. If it doesn't suit you, please contact us.");
        if (event.previousScheduledAt() != null) {
            paragraphs.add("It was previously on " + formatWhen(event.previousScheduledAt()) + ".");
        }
        send(event.appointment(), "Appointment rescheduled", new Body(
            "Your appointment was rescheduled", paragraphs, "View appointment", emailService.appointmentsUrl()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        send(event.appointment(), "Appointment cancelled", new Body(
            "Your appointment was cancelled",
            List.of(event.isSelfService()
                ? "Your appointment has been cancelled as you asked. You're welcome to book a new time whenever you like."
                : "Your appointment has been cancelled by our team. If you didn't expect this, please contact us."),
            "Book again", emailService.bookingUrl()));
    }

    // ── Helpers ──────────────────────────────────────────

    private record Body(String heading, List<String> paragraphs, String ctaLabel, String ctaUrl) {}

    private void send(Appointment appointment, String subject, Body body) {
        try {
            if (appointment.getUser() == null) return;
            User customer = userRepository.findById(appointment.getUser().getId()).orElse(null);
            if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) return;

            AppointmentEmailContent content = new AppointmentEmailContent(
                body.heading(), customer.getFullName(), body.paragraphs(), details(appointment),
                body.ctaLabel(), body.ctaUrl());
            emailService.sendAppointmentEmail(customer.getEmail(),
                subject + ": " + appointment.getAppointmentCode(), content);
        } catch (Exception e) {
            log.error("Failed to email appointment {} ({})", appointment.getId(), subject, e);
        }
    }

    private List<Detail> details(Appointment appointment) {
        List<Detail> rows = new ArrayList<>();
        rows.add(new Detail("Reference", appointment.getAppointmentCode()));
        rows.add(new Detail("Service", appointment.getServiceName()));
        rows.add(new Detail("When", formatWhen(appointment.getScheduledAt())));
        rows.add(new Detail("Duration", appointment.getDurationMinutes() + " minutes"));
        rows.add(new Detail("Where", where(appointment)));
        if (appointment.getGuests() != null && appointment.getGuests() > 1) {
            rows.add(new Detail("Guests", String.valueOf(appointment.getGuests())));
        }
        return rows;
    }

    private String where(Appointment appointment) {
        if (appointment.getServiceType() == AppointmentServiceType.HOME_SERVICE) {
            Map<String, Object> address = appointment.getHomeAddress();
            if (address == null) return "At your home";
            List<String> parts = new ArrayList<>();
            for (String key : List.of("line1", "line2", "city", "state", "postal")) {
                Object value = address.get(key);
                if (value != null && !value.toString().isBlank()) parts.add(value.toString());
            }
            return "At your home: " + String.join(", ", parts);
        }
        if (appointment.getBranch() == null) return "In salon";
        return branchRepository.findById(appointment.getBranch().getId())
            .map(branch -> branch.getName())
            .orElse("In salon");
    }

    private String formatWhen(OffsetDateTime when) {
        return when == null ? "To be confirmed" : WHEN_FORMAT.format(when);
    }
}
