/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaPropertyTransformer;
import io.camunda.connector.kafka.model.KafkaConnectionConfiguration;
import io.camunda.connector.kafka.model.KafkaConnectionValidator;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.outbound.KafkaConnectorFunction;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorResponse;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.test.utils.DockerImages;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SlowTest
@Isolated("The default validator client uses the test-scoped JVM truststore")
@Timeout(90)
class KafkaCredentialIntegrationTest {

  private static final String USERNAME = "connector-user";
  private static final String PASSWORD = "connector-test-password";
  private static final String STORE_PASSWORD = "test-store-password";
  private static final String LOGIN =
      "org.apache.kafka.common.security.plain.PlainLoginModule required "
          + "username=\""
          + USERNAME
          + "\" password=\""
          + PASSWORD
          + "\";";
  private static final Map<String, Object> CLIENT_TIMEOUTS =
      Map.of(
          "request.timeout.ms", 10000,
          "default.api.timeout.ms", 20000,
          "max.block.ms", 20000,
          "delivery.timeout.ms", 20000);
  private static final List<String> TRUSTSTORE_PROPERTIES =
      List.of(
          "javax.net.ssl.trustStore",
          "javax.net.ssl.trustStorePassword",
          "javax.net.ssl.trustStoreType");
  private static final Map<String, String> ORIGINAL_TRUSTSTORE_PROPERTIES = new HashMap<>();
  private static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse(DockerImages.get("kafka"))) {
        @Override
        public String getBootstrapServers() {
          return super.getBootstrapServers().replace("PLAINTEXT://", "SASL_SSL://");
        }
      };
  private static Path tlsDirectory;
  private static String bootstrapServers;

  @BeforeAll
  static void startAuthenticatedBroker() throws Exception {
    tlsDirectory =
        Files.createDirectories(Path.of("target", "kafka-credential-tls-" + UUID.randomUUID()));
    var keyStore = tlsDirectory.resolve("broker.jks");
    var certificate = tlsDirectory.resolve("broker.crt");
    var trustStore = tlsDirectory.resolve("truststore.jks");
    String host = KAFKA.getHost();
    String subjectAlternativeName =
        host.matches("[0-9.]+") || host.contains(":") ? "ip:" + host : "dns:" + host;
    keytool(
        "-genkeypair",
        "-alias",
        "broker",
        "-keyalg",
        "RSA",
        "-keysize",
        "2048",
        "-validity",
        "2",
        "-dname",
        "CN=" + host,
        "-ext",
        "SAN=" + subjectAlternativeName,
        "-keystore",
        keyStore.toString(),
        "-storetype",
        "JKS",
        "-storepass",
        STORE_PASSWORD,
        "-keypass",
        STORE_PASSWORD);
    keytool(
        "-exportcert",
        "-alias",
        "broker",
        "-keystore",
        keyStore.toString(),
        "-storepass",
        STORE_PASSWORD,
        "-file",
        certificate.toString());
    keytool(
        "-importcert",
        "-noprompt",
        "-alias",
        "broker",
        "-file",
        certificate.toString(),
        "-keystore",
        trustStore.toString(),
        "-storetype",
        "JKS",
        "-storepass",
        STORE_PASSWORD);

    // The validator has no per-use TLS overrides; isolate and restore its default JVM trust.
    for (String property : TRUSTSTORE_PROPERTIES) {
      ORIGINAL_TRUSTSTORE_PROPERTIES.put(property, System.getProperty(property));
    }
    System.setProperty("javax.net.ssl.trustStore", trustStore.toAbsolutePath().toString());
    System.setProperty("javax.net.ssl.trustStorePassword", STORE_PASSWORD);
    System.setProperty("javax.net.ssl.trustStoreType", "JKS");

    KAFKA
        .withKraft()
        .withStartupTimeout(Duration.ofSeconds(90))
        .withEnv("KAFKA_LISTENERS", "SASL_SSL://0.0.0.0:9093,BROKER://0.0.0.0:9092")
        .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "SASL_SSL:SASL_SSL,BROKER:PLAINTEXT")
        .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "PLAIN")
        .withEnv(
            "KAFKA_LISTENER_NAME_SASL__SSL_PLAIN_SASL_JAAS_CONFIG",
            "org.apache.kafka.common.security.plain.PlainLoginModule required user_"
                + USERNAME
                + "=\""
                + PASSWORD
                + "\";")
        .withEnv("KAFKA_SSL_KEYSTORE_FILENAME", "broker.jks")
        .withEnv("KAFKA_SSL_KEYSTORE_CREDENTIALS", "store-password")
        .withEnv("KAFKA_SSL_KEY_CREDENTIALS", "store-password")
        .withEnv("KAFKA_SSL_KEYSTORE_TYPE", "JKS")
        .withEnv("KAFKA_SSL_CLIENT_AUTH", "none")
        .withEnv(
            "KAFKA_OPTS", "-Djava.security.auth.login.config=/etc/kafka/secrets/kafka-jaas.conf")
        .withCopyFileToContainer(
            MountableFile.forHostPath(keyStore, 0644), "/etc/kafka/secrets/broker.jks")
        .withCopyToContainer(
            Transferable.of(STORE_PASSWORD, 0644), "/etc/kafka/secrets/store-password")
        .withCopyToContainer(
            Transferable.of("KafkaServer { " + LOGIN + " };", 0644),
            "/etc/kafka/secrets/kafka-jaas.conf");
    KAFKA.start();
    bootstrapServers = KAFKA.getBootstrapServers().replace("SASL_SSL://", "");
  }

  @AfterAll
  static void stopAuthenticatedBroker() throws Exception {
    try {
      KAFKA.stop();
    } finally {
      ORIGINAL_TRUSTSTORE_PROPERTIES.forEach(
          (property, originalValue) -> {
            if (originalValue == null) {
              System.clearProperty(property);
            } else {
              System.setProperty(property, originalValue);
            }
          });
      if (tlsDirectory != null) {
        try (var files = Files.walk(tlsDirectory)) {
          for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
            Files.deleteIfExists(file);
          }
        }
      }
    }
  }

  @Test
  void publishesAndConsumesUsingCredentialInsteadOfStaleInlineConnection() throws Exception {
    publishAndConsume(credential(), CLIENT_TIMEOUTS);
  }

  @Test
  void additionalPropertiesRemainFinalConnectionOverrides() throws Exception {
    var overrides = new HashMap<>(CLIENT_TIMEOUTS);
    overrides.put("bootstrap.servers", bootstrapServers);
    overrides.put("sasl.jaas.config", LOGIN);
    publishAndConsume(
        new KafkaConnectionConfiguration("unused.invalid:9093", "unused-user", "unused-password"),
        overrides);
  }

  @Test
  void validatorAcceptsAuthenticatedTlsConnection() {
    assertThat(new KafkaConnectionValidator().validate(credential()))
        .isEqualTo(ConfigurationValidationResult.success());
  }

  @Test
  void validatorRejectsWrongPassword() {
    var result =
        new KafkaConnectionValidator()
            .validate(
                new KafkaConnectionConfiguration(bootstrapServers, USERNAME, "wrong-password"));

    assertThat(result.status()).isEqualTo(ConfigurationValidationResult.Status.FAILURE);
    assertThat(result.code())
        .isEqualTo(ConfigurationValidationResult.ErrorCode.UNAUTHORIZED.name());
    assertThat(result.message()).doesNotContain(PASSWORD, "wrong-password");
  }

  private static void publishAndConsume(
      KafkaConnectionConfiguration credential, Map<String, Object> additionalProperties)
      throws Exception {
    String topic = "credential-test-" + UUID.randomUUID();
    var mapper = ConnectorsObjectMapperSupplier.getCopy();
    var inputs = new HashMap<String, Object>();
    inputs.put("kafkaConnectionConfiguration", credential);
    inputs.put("authentication", Map.of("username", "stale-user", "password", "stale-password"));
    inputs.put("topic", Map.of("bootstrapServers", "stale.invalid:9093", "topicName", topic));
    inputs.put("message", Map.of("key", "credential-key", "value", "authenticated-message"));
    inputs.put("additionalProperties", additionalProperties);
    var request = mapper.convertValue(inputs, KafkaConnectorRequest.class);
    var producerProperties = KafkaPropertiesUtil.assembleKafkaClientProperties(request);
    assertSecureConnection(producerProperties);
    try (var admin = Admin.create(producerProperties)) {
      admin
          .createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
          .all()
          .get(20, TimeUnit.SECONDS);
    }
    var result =
        new KafkaConnectorFunction()
            .execute(OutboundConnectorContextBuilder.create().variables(inputs).build());
    assertThat(result).isInstanceOf(KafkaConnectorResponse.class);
    assertThat(((KafkaConnectorResponse) result).topic()).isEqualTo(topic);

    inputs.remove("message");
    inputs.put("groupId", "credential-group-" + UUID.randomUUID());
    inputs.put("autoOffsetReset", "earliest");
    var inbound = mapper.convertValue(inputs, KafkaConnectorProperties.class);
    var consumerProperties = KafkaPropertyTransformer.getKafkaProperties(inbound, null);
    assertSecureConnection(consumerProperties);
    try (var consumer = new KafkaConsumer<String, String>(consumerProperties)) {
      consumer.subscribe(List.of(topic));
      var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < deadline) {
        var records = consumer.poll(Duration.ofSeconds(1));
        if (!records.isEmpty()) {
          assertThat(records.records(topic))
              .singleElement()
              .satisfies(
                  record -> {
                    assertThat(record.key()).isEqualTo("credential-key");
                    assertThat(record.value()).isEqualTo("authenticated-message");
                  });
          return;
        }
      }
      throw new AssertionError("No authenticated Kafka message received within 30 seconds");
    }
  }

  private static void assertSecureConnection(Map<?, ?> properties) {
    assertThat(properties.get("bootstrap.servers")).isEqualTo(bootstrapServers);
    assertThat(properties.get("security.protocol")).isEqualTo("SASL_SSL");
    assertThat(properties.get("sasl.mechanism")).isEqualTo("PLAIN");
    assertThat(properties.get("sasl.jaas.config").toString()).contains(USERNAME, PASSWORD);
    assertThat(properties.containsKey(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG))
        .isFalse();
    assertThat(SslConfigs.DEFAULT_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM).isEqualTo("https");
  }

  private static KafkaConnectionConfiguration credential() {
    return new KafkaConnectionConfiguration(bootstrapServers, USERNAME, PASSWORD);
  }

  private static void keytool(String... arguments) throws Exception {
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
    command.addAll(List.of(arguments));
    var output = tlsDirectory.resolve("keytool.log");
    var process =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("keytool finished").isTrue();
      assertThat(process.exitValue()).as(Files.readString(output)).isZero();
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }
}
