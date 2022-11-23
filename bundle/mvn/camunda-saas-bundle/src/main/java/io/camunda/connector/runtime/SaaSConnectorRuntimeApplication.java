package io.camunda.connector.runtime;

import io.camunda.zeebe.spring.client.EnableZeebeClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableZeebeClient
public class SaaSConnectorRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaaSConnectorRuntimeApplication.class, args);
    }

}
