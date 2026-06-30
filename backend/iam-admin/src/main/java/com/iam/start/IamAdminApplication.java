package com.iam.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = "com.iam")
@EntityScan(basePackages = "com.iam.infrastructure.entity")
@EnableJpaRepositories(basePackages = "com.iam.infrastructure.repository")
@EnableAsync
public class IamAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(IamAdminApplication.class, args);
    }
}
