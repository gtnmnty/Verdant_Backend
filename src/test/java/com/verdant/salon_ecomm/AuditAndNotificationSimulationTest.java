package com.verdant.salon_ecomm;

import com.verdant.salon_ecomm.dtos.appointment.CreateAppointmentInput;
import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.entities.PendingCleanUpJob;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;

import com.verdant.salon_ecomm.repositories.*;
import com.verdant.salon_ecomm.services.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

@SpringBootTest
public class AuditAndNotificationSimulationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SalonServiceRepository salonServiceRepository;

    @Autowired
    private StylistRepository stylistRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CleanUpJobRepository cleanUpJobRepository;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private UserService userService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User testCustomer;
    private User testAdmin;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public EmailService emailService() throws Exception {
            EmailService mockService = mock(EmailService.class);
            doNothing().when(mockService).sendVerificationEmail(anyString(), anyString());
            return mockService;
        }

        @Bean
        @Primary
        public CloudinaryService cloudinaryService() {
            CloudinaryService mockService = mock(CloudinaryService.class);
            doNothing().when(mockService).delete(anyString());
            return mockService;
        }

        @Bean
        @Primary
        public PaymentService paymentService() {
            PaymentService mockService = mock(PaymentService.class);
            doNothing().when(mockService).deleteStripeCustomer(anyString());
            return mockService;
        }
    }

    @BeforeEach
    public void setUp() {
        // Repair database values to match Java enums
        try {
            jdbcTemplate.execute("UPDATE stylists SET status = 'ACTIVE' WHERE status = 'active'");
            jdbcTemplate.execute("UPDATE stylists SET status = 'INACTIVE' WHERE status = 'inactive'");
            jdbcTemplate.execute("UPDATE salon_services SET status = 'ACTIVE' WHERE status = 'active'");
            jdbcTemplate.execute("UPDATE products SET status = 'ACTIVE' WHERE status = 'active'");
        } catch (Exception e) {
            System.err.println("Warning: DB status enum repair update failed: " + e.getMessage());
        }

        // Create test customer
        String customerId = UUID.randomUUID().toString().substring(0, 8);
        testCustomer = new User();
        testCustomer.setFullName("Test Customer " + customerId);
        testCustomer.setEmail("customer_" + customerId + "@example.com");
        testCustomer.setPasswordHash("hashed_password");
        testCustomer.setRole(AccountRole.CUSTOMER);
        testCustomer.setEnabled(true);
        testCustomer.setStatus(AccountStatus.ACTIVE);
        testCustomer.setEmailVerified(true);
        testCustomer = userRepository.save(testCustomer);

        // Create test admin/staff
        String adminId = UUID.randomUUID().toString().substring(0, 8);
        testAdmin = new User();
        testAdmin.setFullName("Test Admin " + adminId);
        testAdmin.setEmail("admin_" + adminId + "@example.com");
        testAdmin.setPasswordHash("hashed_password");
        testAdmin.setRole(AccountRole.ADMIN);
        testAdmin.setEnabled(true);
        testAdmin.setStatus(AccountStatus.ACTIVE);
        testAdmin.setEmailVerified(true);
        testAdmin = userRepository.save(testAdmin);
    }

    @Test
    public void testAuditAndNotificationE2E() throws Exception {
        System.out.println("========== AUDIT & NOTIFICATION SIMULATION START ==========");

        // Fetch pre-populated master data from Supabase
        List<SalonService> services = salonServiceRepository.findAll();
        assertFalse(services.isEmpty(), "No services found in Supabase database");
        SalonService targetService = services.getFirst();

        List<Branch> branches = branchRepository.findAll();
        assertFalse(branches.isEmpty(), "No branches found in Supabase database");
        Branch targetBranch = branches.getFirst();

        List<Stylist> stylists = stylistRepository.findAll();
        Stylist targetStylist = stylists.isEmpty() ? null : stylists.getFirst();

        System.out.println("Using Service: " + targetService.getName());
        System.out.println("Using Branch: " + targetBranch.getName());
        if (targetStylist != null) {
            System.out.println("Using Stylist: " + targetStylist.getName());
        }

        // --- Step 1: User Books an Appointment ---
        System.out.println("--- STEP 1: Customer booking appointment ---");
        CreateAppointmentInput bookingInput = new CreateAppointmentInput(
            testCustomer.getId(),
            targetService.getId(),
            targetStylist != null ? targetStylist.getId() : null,
            AppointmentServiceType.IN_SALON,
            targetBranch.getId(),
            OffsetDateTime.now().plusDays(2).plusMinutes(Math.abs(testCustomer.getId().hashCode()) % 10000),
            1,
            null,
            "Test booking details"
        );

        var appointment = appointmentService.bookAppointment(bookingInput, testCustomer.getId());
        assertNotNull(appointment);
        System.out.println("Appointment booked: " + appointment.getAppointmentCode());

        // Wait brief time for async events to publish and commit if any,
        // though TransactionalEventListener (AFTER_COMMIT) triggers on transactional commit.
        // Verify Audit Log generated for booking
        var auditLogs = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE entity_type = 'APPOINTMENT' AND entity_id = ? AND action_type = 'BOOKED'",
                appointment.getId().toString());
        assertFalse(auditLogs.isEmpty(), "Booking audit log not found");
        System.out.println("Verified: BOOKED audit log successfully created.");

        // Verify Notifications generated for customer and admin
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var customerNotifications = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ?", testCustomer.getId());
            assertFalse(customerNotifications.isEmpty(), "Customer notifications list was empty");
        });
        System.out.println("Verified: Customer received booking notification.");

        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var adminNotifications = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ?", testAdmin.getId());
            assertFalse(adminNotifications.isEmpty(), "Admin notifications list was empty");
        });
        System.out.println("Verified: Admin received booking notification.");

        // --- Step 2: Admin Reschedules the Appointment (Elevated Action) ---
        System.out.println("--- STEP 2: Admin rescheduling appointment ---");
        OffsetDateTime newTime = OffsetDateTime.now().plusDays(3).plusMinutes(Math.abs(testAdmin.getId().hashCode()) % 10000);
        var rescheduled = appointmentService.rescheduleAppointment(
            appointment.getId(),
            newTime,
            testAdmin.getId(),
            true
        );
        assertNotNull(rescheduled);
        System.out.println("Appointment rescheduled to: " + newTime);

        // Verify Audit Log has Admin as the actor
        auditLogs = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE entity_type = 'APPOINTMENT' AND entity_id = ? AND action_type = 'RESCHEDULED' AND actor_id = ?",
                appointment.getId().toString(), testAdmin.getId());
        assertFalse(auditLogs.isEmpty(), "Reschedule audit log with Admin actor not found");
        System.out.println("Verified: RESCHEDULED audit log created, actor set to Admin user: " + testAdmin.getFullName());

        // Verify Customer received notification showing Admin performed reschedule
        var customerNotifications = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ? AND actor_name = ?",
                testCustomer.getId(), testAdmin.getFullName());
        assertFalse(customerNotifications.isEmpty(), "Customer notification should state Admin performed the action");
        System.out.println("Verified: Customer notified of rescheduling action with actorName '" + testAdmin.getFullName() + "'.");

        // --- Step 3: Non-Human / System Event (Background Cleanup Job) ---
        System.out.println("--- STEP 3: Simulating non-human background cleanup job ---");
        UUID randomUserId = UUID.randomUUID();
        PendingCleanUpJob cleanupJob = PendingCleanUpJob.builder()
                .userId(randomUserId)
                .avatarPublicId("avatar_test_public_id")
                .stripeCustomerId("stripe_test_customer_id")
                .build();
        cleanUpJobRepository.save(cleanupJob);

        // Programmatically run the scheduled task (non-human actor)
        userService.processPendingCleanupJobs();

        // Verify the job was claimed and marked processed
        List<PendingCleanUpJob> processedJobs = cleanUpJobRepository.findAll().stream()
                .filter(j -> j.getUserId().equals(randomUserId) && j.isProcessed())
                .toList();
        assertFalse(processedJobs.isEmpty(), "System cleanup job failed to process");
        System.out.println("Verified: Non-human system job ran and marked processed.");

        System.out.println("========== AUDIT & NOTIFICATION SIMULATION ENDED SUCCESSFUL ==========");
    }
}
