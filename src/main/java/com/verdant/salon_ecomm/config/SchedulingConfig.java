package com.verdant.salon_ecomm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// H-8: without @EnableScheduling, Spring ignores every @Scheduled method, so none of the
// background jobs ever ran. The jobs this turns on:
//   - DeletedUserCleanupJob  (every 60s)  Stripe customer + Cloudinary avatar of self-deleted users
//   - UnpaidOrderExpiryJob   (every 5min) cancels unpaid orders past their TTL and returns stock
//   - GiftCardSweepJob       (hourly)     expires gift cards, cancels abandoned gift card purchases
//
// Running more than one instance: every job claims its rows before working on them
// (claim token + lease, or SELECT ... FOR UPDATE SKIP LOCKED), so two instances running the same
// job at once split the work instead of doing it twice. That is why there is no ShedLock here.
// A new job must follow the same rule, or add a distributed lock.
//
// Thread pool: Spring Boot's scheduler has ONE thread by default, so a slow Stripe/Cloudinary
// call in one job would delay all the others. spring.task.scheduling.pool.size in
// application.properties gives each job its own thread. A fixedDelay job still never overlaps
// itself on one instance, whatever the pool size.
//
// app.scheduling.enabled=false turns every job off (tests, one-off tooling, a read-only replica).
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
