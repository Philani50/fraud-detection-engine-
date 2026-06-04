package za.co.capitecbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Fraud Detection Engine service.
 *
 * <p>Spring Boot automatically scans all sub-packages under {@code za.co.capitecbank} for
 * components, entities, and repositories — no manual configuration needed.
 * {@code @ConfigurationPropertiesScan} discovers all {@code @ConfigurationProperties} records
 * (e.g. {@code EncryptionProperties}, {@code RuleEngineConfig}) without requiring
 * {@code @EnableConfigurationProperties} on each individual config class.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@SuppressWarnings("HideUtilityClassConstructor")
public class Application {

    public static void main(final String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }
}
