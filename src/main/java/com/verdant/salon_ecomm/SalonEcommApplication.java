package com.verdant.salon_ecomm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.verdant.salon_ecomm.repositories")
public class SalonEcommApplication {
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(SalonEcommApplication.class);
		app.addListeners(new RequireActiveProfile());
		app.run(args);
	}

	// M-6: refuse to start without an explicit profile. application.properties used to default to
	// "local", so a deployment that forgot SPRING_PROFILES_ACTIVE ran with GraphiQL, DEBUG web logging
	// and a non-Secure refresh cookie, with nothing in the logs to say so.
	// Runs on ApplicationEnvironmentPreparedEvent: profiles are known by then, but no bean (DB pool,
	// Redis, Stripe) has been created, so the failure is immediate and the message is the first error.
	// Registered here rather than in spring.factories, so tests (@SpringBootTest) choose their own profile.
	static final class RequireActiveProfile implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
		@Override
		public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
			if (event.getEnvironment().getActiveProfiles().length == 0) {
				throw new IllegalStateException(
					"No Spring profile is active. Set SPRING_PROFILES_ACTIVE (or -Dspring.profiles.active) "
						+ "to local, dev or docker.");
			}
		}
	}
}
