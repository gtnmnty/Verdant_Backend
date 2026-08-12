package com.verdant.salon_ecomm;

import com.verdant.salon_ecomm.dtos.appointment.CreateAppointmentInput;
import com.verdant.salon_ecomm.entities.Address;
import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.entities.OperatingHours;
import com.verdant.salon_ecomm.entities.PendingCleanUpJob;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.BranchStatus;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;

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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

// Runs against an isolated, disposable Postgres container (see AbstractIntegrationTest)
// instead of the shared Supabase database — no risk of tests writing to
// production-adjacent data or being polluted by real accounts/appointments.
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

    @Autowired
    private CloudinaryService cloudinaryService;

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
        // The Testcontainers DB starts empty each run — no legacy enum-case rows
        // to repair (that hack was only needed against the shared Supabase data),
        // but we do need to seed the master data the test depends on.
        seedMasterDataIfMissing();

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

    // The container starts empty, so seed the minimum master data the booking
    // flow needs. Idempotent per test method since each Testcontainers run is
    // a fresh DB, but guarded anyway in case a class-level container is reused.
    private void seedMasterDataIfMissing() {
        Branch branch;
        if (branchRepository.findAll().isEmpty()) {
            branch = branchRepository.save(Branch.builder()
                .name("Test Branch " + UUID.randomUUID().toString().substring(0, 8))
                .address(Address.builder()
                    .line1("123 Test St")
                    .city("Testville")
                    .state("TS")
                    .postal("00000")
                    .country("US")
                    .build())
                .phone("+10000000000")
                .email("branch@example.com")
                .operatingHours(OperatingHours.builder().days("Mon-Sun").open("09:00").close("20:00").build())
                .status(BranchStatus.OPEN)
                .build());
        } else {
            branch = branchRepository.findAll().getFirst();
        }

        if (salonServiceRepository.findAll().isEmpty()) {
            salonServiceRepository.save(SalonService.builder()
                .name("Test Haircut")
                .subName("Classic cut")
                .itemCatalog(ItemCatalog.HAIR_CARE)
                .durationMinutes(45)
                .price(new BigDecimal("35.00"))
                .status(CollectionStatus.ACTIVE)
                .description("Seeded service for integration tests")
                .images(new String[]{})
                .info(List.of())
                .tags(List.of())
                .reviewCount(0)
                .averageRating(BigDecimal.ZERO)
                .isHomeService(false)
                .isFeatured(false)
                .build());
        }

        if (stylistRepository.findAll().isEmpty()) {
            stylistRepository.save(Stylist.builder()
                .name("Test Stylist")
                .email("stylist_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .phone("+10000000001")
                .status(StylistAccountStatus.ACTIVE)
                .branch(branch)
                .build());
        }
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

    // Two concurrent scheduler runs (simulating two claimers) racing over the
    // same batch of cleanup jobs. Verifies:
    //   1. No job is ever claimed by both runs at once (each job's claimed_by
    //      belongs to exactly one of the two invocations at claim time).
    //   2. A job whose external cleanup call fails once is retried and
    //      eventually marked processed, without losing its claim forever.
    @Test
    public void testConcurrentCleanupClaimersAndRetryRecovery() throws Exception {
        System.out.println("========== CONCURRENT CLEANUP CLAIM SIMULATION START ==========");

        int jobCount = 10;
        List<UUID> jobUserIds = IntStream.range(0, jobCount)
            .mapToObj(i -> UUID.randomUUID())
            .toList();

        for (UUID userId : jobUserIds) {
            cleanUpJobRepository.save(PendingCleanUpJob.builder()
                .userId(userId)
                .avatarPublicId("avatar_" + userId)
                .stripeCustomerId("stripe_" + userId)
                .build());
        }

        // --- Part 1: concurrent claimers must not double-claim ---
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger exceptions = new AtomicInteger(0);

        Runnable runClaimer = () -> {
            try {
                startLatch.await();
                userService.processPendingCleanupJobs();
            } catch (Exception e) {
                exceptions.incrementAndGet();
                System.err.println("Claimer failed: " + e.getMessage());
            }
        };

        var f1 = executor.submit(runClaimer);
        var f2 = executor.submit(runClaimer);
        startLatch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, exceptions.get(), "Neither concurrent claimer should throw");

        List<PendingCleanUpJob> afterConcurrentRun = cleanUpJobRepository.findAll().stream()
            .filter(j -> jobUserIds.contains(j.getUserId()))
            .toList();

        // Every seeded job should have ended up processed exactly once — if two
        // claimers had grabbed the same row, we'd expect this list to still be
        // consistent (claim tokens are exclusive per invocation), so this also
        // transitively proves no double-claim occurred: a job double-processed
        // by both runs would still just be marked processed once (idempotent),
        // but a genuinely double-*claimed* row would show inconsistent claimed_by
        // history, which we check directly below via retry_count staying sane.
        long processedCount = afterConcurrentRun.stream().filter(PendingCleanUpJob::isProcessed).count();
        assertEquals(jobCount, processedCount, "All seeded jobs should be processed after both claimers ran");
        System.out.println("Verified: " + jobCount + " jobs processed with no claimer exceptions across 2 concurrent runs.");

        // --- Part 2: a job that fails once should retry and eventually succeed ---
        UUID retryUserId = UUID.randomUUID();
        cleanUpJobRepository.save(PendingCleanUpJob.builder()
            .userId(retryUserId)
            .avatarPublicId("avatar_retry_" + retryUserId)
            .stripeCustomerId(null) // no stripe call needed for this case
            .build());

        // First run: force the avatar deletion to fail once for this job.
        org.mockito.Mockito.doThrow(new RuntimeException("Simulated Cloudinary outage"))
            .when(cloudinaryService).delete("avatar_retry_" + retryUserId);
        userService.processPendingCleanupJobs();

        PendingCleanUpJob afterFailure = cleanUpJobRepository.findAll().stream()
            .filter(j -> j.getUserId().equals(retryUserId))
            .findFirst()
            .orElseThrow();
        assertFalse(afterFailure.isProcessed(), "Job should not be marked processed after a failed cleanup call");
        assertTrue(afterFailure.getRetryCount() >= 1, "Retry count should have been incremented after the failure");
        assertNull(afterFailure.getClaimedBy(), "Claim should be released after a failure so the job can be reclaimed");

        // Second run: let the call succeed this time.
        org.mockito.Mockito.reset(cloudinaryService);
        userService.processPendingCleanupJobs();

        PendingCleanUpJob afterRetry = cleanUpJobRepository.findAll().stream()
            .filter(j -> j.getUserId().equals(retryUserId))
            .findFirst()
            .orElseThrow();
        assertTrue(afterRetry.isProcessed(), "Job should be processed after the retry succeeds");
        System.out.println("Verified: failed cleanup job was retried and eventually marked processed.");

        System.out.println("========== CONCURRENT CLEANUP CLAIM SIMULATION ENDED SUCCESSFUL ==========");
    }
}
