/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaPropertyTransformer;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaMessage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KafkaAdditionalPropertiesValidatorTest {

  private static final String STRING_SERIALIZER =
      "org.apache.kafka.common.serialization.StringSerializer";
  private static final KafkaTopic TOPIC = new KafkaTopic("localhost:9092", "topic");

  static Stream<Arguments> rejected() {
    return Stream.of(
        // config providers read files and environment variables of the runtime
        Arguments.of("config.providers", "env"),
        Arguments.of(
            "config.providers.env.class",
            "org.apache.kafka.common.config.provider.EnvVarConfigProvider"),
        Arguments.of("config.providers.dir.param.allowed.paths", "/"),
        Arguments.of("Config.Providers", "env"),
        Arguments.of(" config.providers ", "env"),
        // login modules other than a username and password
        Arguments.of(
            "sasl.jaas.config",
            "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true keyTab=\"/etc/passwd\";"),
        Arguments.of(
            "sasl.jaas.config",
            "com.sun.security.auth.module.JndiLoginModule required user.provider.url=\"ldap://a/b\" group.provider.url=\"ldap://a/c\";"),
        Arguments.of(
            "sasl.jaas.config",
            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;"),
        Arguments.of(
            "sasl.jaas.config", "\"com.sun.security.auth.module.Krb5LoginModule\" required;"),
        Arguments.of(
            "sasl.jaas.config",
            "/* org.apache.kafka.common.security.plain.PlainLoginModule */ com.sun.security.auth.module.Krb5LoginModule required;"),
        Arguments.of(
            "sasl.jaas.config",
            "org.apache.kafka.common.security.plain.PlainLoginModule required username='u' password='p'; com.sun.security.auth.module.Krb5LoginModule required;"),
        Arguments.of("sasl.jaas.config", "not a JAAS configuration"),
        Arguments.of("sasl.jaas.config", ""),
        Arguments.of("sasl.jaas.config", List.of("x")),
        Arguments.of("SASL.JAAS.CONFIG", "com.sun.security.auth.module.Krb5LoginModule required;"),
        // OAuth settings read a token or an assertion from a file or a URL
        Arguments.of("sasl.oauthbearer.token.endpoint.url", "file:///etc/passwd"),
        Arguments.of("sasl.oauthbearer.token.endpoint.url", "https://example.com/token"),
        Arguments.of("sasl.oauthbearer.assertion.file", "/etc/passwd"),
        Arguments.of("sasl.oauthbearer.assertion.private.key.file", "/etc/passwd"),
        // Kerberos settings include a command to run
        Arguments.of("sasl.kerberos.kinit.cmd", "/bin/sh"),
        Arguments.of("sasl.kerberos.service.name", "kafka"),
        // classes loaded by name
        Arguments.of("interceptor.classes", "a.b.Interceptor"),
        Arguments.of("metric.reporters", "a.b.Reporter"),
        Arguments.of("security.providers", "a.b.Creator"),
        Arguments.of("partitioner.class", "a.b.Partitioner"),
        Arguments.of("partition.assignment.strategy", "a.b.Assignor"),
        Arguments.of("ssl.engine.factory.class", "a.b.Factory"),
        Arguments.of("sasl.login.class", "a.b.Login"),
        Arguments.of(
            "sasl.login.callback.handler.class",
            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler"),
        Arguments.of("sasl.client.callback.handler.class", "a.b.Handler"),
        Arguments.of("key.serializer", "a.b.Serializer"),
        Arguments.of("value.serializer", KafkaAdditionalPropertiesValidator.class),
        Arguments.of("value.serializer", List.of(STRING_SERIALIZER, "a.b.Serializer")),
        Arguments.of("value.serializer", STRING_SERIALIZER + ",a.b.Serializer"),
        Arguments.of("value.serializer", 1),
        Arguments.of("key.deserializer", "org.apache.kafka.common.serialization.a.Deserializer"),
        Arguments.of("some.future.setting.class", "a.b.C"),
        // schema registry client
        Arguments.of("value.subject.name.strategy", "a.b.Strategy"),
        Arguments.of("context.name.strategy", "a.b.Strategy"),
        Arguments.of("rule.executors", "x"),
        Arguments.of("rule.executors.x.class", "a.b.Executor"),
        Arguments.of("rule.actions", "x"),
        Arguments.of("json.value.type", "a.b.Type"),
        Arguments.of("specific.avro.value.type", "a.b.Type"),
        Arguments.of("bearer.auth.custom.provider.class", "a.b.Provider"),
        Arguments.of("bearer.auth.credentials.source", "CUSTOM"),
        Arguments.of("bearer.auth.credentials.source", "UAMI"),
        Arguments.of("bearer.auth.credentials.source", "SASL_OAUTHBEARER_INHERIT"),
        Arguments.of("bearer.auth.uami.endpoint.url", "http://169.254.169.254/"),
        // the schema registry client strips this prefix before reading its settings
        Arguments.of("schema.registry.bearer.auth.credentials.source", "UAMI"),
        Arguments.of("schema.registry.bearer.auth.uami.endpoint.url", "http://169.254.169.254/"),
        Arguments.of("schema.registry.bearer.auth.custom.provider.class", "a.b.Provider"),
        Arguments.of("Schema.Registry.Rule.Executors", "x"),
        // ListSerializer and ListDeserializer instantiate these
        Arguments.of("default.list.key.serde.inner", "a.b.Serde"),
        Arguments.of("default.list.value.serde.inner", "a.b.Serde"),
        Arguments.of("default.list.key.serde.type", "a.b.List"),
        Arguments.of("default.list.value.serde.type", "a.b.List"));
  }

  @ParameterizedTest
  @MethodSource("rejected")
  void rejectsUnsafeProperty(String key, Object value) {
    assertThatThrownBy(() -> KafkaAdditionalPropertiesValidator.validate(Map.of(key, value)))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("is not allowed");
  }

  static Stream<Arguments> allowed() {
    return Stream.of(
        Arguments.of("client.id", "my-client"),
        Arguments.of("client.id", "${env:HOSTNAME}"),
        Arguments.of("max.block.ms", "8000"),
        Arguments.of("acks", "1"),
        Arguments.of("linger.ms", 5),
        Arguments.of("enable.idempotence", false),
        Arguments.of("compression.type", "gzip"),
        Arguments.of("group.id", "my-group"),
        Arguments.of("bootstrap.servers", "broker:9092"),
        Arguments.of("security.protocol", "SASL_SSL"),
        Arguments.of("sasl.mechanism", "SCRAM-SHA-512"),
        Arguments.of(
            "sasl.jaas.config",
            "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"u\" password=\"p\";"),
        Arguments.of(
            "sasl.jaas.config",
            "org.apache.kafka.common.security.plain.PlainLoginModule required username='u' password='p';"),
        Arguments.of("sasl.login.refresh.window.factor", 0.8),
        Arguments.of("ssl.truststore.type", "PEM"),
        Arguments.of("ssl.truststore.certificates", "-----BEGIN CERTIFICATE-----"),
        Arguments.of("ssl.endpoint.identification.algorithm", ""),
        Arguments.of("metadata.recovery.strategy", "rebootstrap"),
        Arguments.of("key.serializer", STRING_SERIALIZER),
        Arguments.of("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer"),
        Arguments.of(
            "value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer"),
        Arguments.of(
            "partitioner.class", "org.apache.kafka.clients.producer.RoundRobinPartitioner"),
        Arguments.of(
            "partition.assignment.strategy",
            "org.apache.kafka.clients.consumer.CooperativeStickyAssignor,org.apache.kafka.clients.consumer.RangeAssignor"),
        Arguments.of(
            "partition.assignment.strategy",
            List.of("org.apache.kafka.clients.consumer.RoundRobinAssignor")),
        Arguments.of(
            "value.subject.name.strategy",
            "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy"),
        Arguments.of("interceptor.classes", ""),
        Arguments.of("metric.reporters", "org.apache.kafka.common.metrics.JmxReporter"),
        Arguments.of("schema.registry.url", "https://registry.example.com"),
        Arguments.of("basic.auth.credentials.source", "USER_INFO"),
        Arguments.of("basic.auth.user.info", "user:password"),
        Arguments.of("bearer.auth.credentials.source", "STATIC_TOKEN"),
        Arguments.of("bearer.auth.credentials.source", "OAUTHBEARER"),
        Arguments.of("schema.registry.basic.auth.credentials.source", "USER_INFO"),
        Arguments.of("schema.registry.bearer.auth.credentials.source", "STATIC_TOKEN"),
        Arguments.of("schema.registry.ssl.truststore.type", "PEM"),
        Arguments.of(
            "default.list.value.serde.inner",
            "org.apache.kafka.common.serialization.Serdes$StringSerde"),
        Arguments.of("default.list.value.serde.type", "java.util.ArrayList"),
        Arguments.of("auto.register.schemas", false));
  }

  @ParameterizedTest
  @MethodSource("allowed")
  void allowsOrdinaryProperty(String key, Object value) {
    assertThatNoException()
        .isThrownBy(() -> KafkaAdditionalPropertiesValidator.validate(Map.of(key, value)));
  }

  @Test
  void allowsNoProperties() {
    assertThatNoException().isThrownBy(() -> KafkaAdditionalPropertiesValidator.validate(null));
    assertThatNoException().isThrownBy(() -> KafkaAdditionalPropertiesValidator.validate(Map.of()));
  }

  @Test
  void doesNotPutTheValueInTheMessage() {
    var properties =
        Map.<String, Object>of(
            "sasl.jaas.config",
            "com.sun.security.auth.module.Krb5LoginModule required password=\"the-secret\";");

    assertThatThrownBy(() -> KafkaAdditionalPropertiesValidator.validate(properties))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("sasl.jaas.config")
        .hasMessageNotContaining("the-secret")
        .hasMessageNotContaining("Krb5LoginModule");
  }

  @Test
  void shortensALongKeyInTheMessage() {
    var key = "config.providers." + "x".repeat(500);

    assertThatThrownBy(() -> KafkaAdditionalPropertiesValidator.validate(Map.of(key, "a")))
        .isInstanceOf(ConnectorInputException.class)
        .satisfies(e -> assertThat(e.getMessage().length()).isLessThan(400));
  }

  @Test
  void makesTheKafkaClientRefuseConfigProviders() {
    KafkaAdditionalPropertiesValidator.validate(Map.of("client.id", "x"));

    assertThat(System.getProperty(KafkaAdditionalPropertiesValidator.AUTOMATIC_CONFIG_PROVIDERS))
        .isEqualTo("none");
  }

  @Test
  void outboundPropertiesRejectTheReportedExploit() {
    var additionalProperties = new LinkedHashMap<String, Object>();
    additionalProperties.put("config.providers", "dir");
    additionalProperties.put(
        "config.providers.dir.class",
        "org.apache.kafka.common.config.provider.DirectoryConfigProvider");
    additionalProperties.put("client.id", "K1FILE=${dir:/etc:hostname}");
    additionalProperties.put("max.block.ms", "8000");
    var request =
        new KafkaConnectorRequest(
            null,
            TOPIC,
            new KafkaMessage(null, "x"),
            new NoSchemaStrategy(),
            null,
            additionalProperties);

    assertThatThrownBy(() -> KafkaPropertiesUtil.assembleKafkaClientProperties(request))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("config.providers");
  }

  @Test
  void outboundPropertiesKeepOrdinaryAdditionalProperties() {
    var request =
        new KafkaConnectorRequest(
            null,
            TOPIC,
            new KafkaMessage(null, "x"),
            new NoSchemaStrategy(),
            null,
            Map.of("client.id", "camunda-control-k0", "max.block.ms", "8000"));

    var properties = KafkaPropertiesUtil.assembleKafkaClientProperties(request);

    assertThat(properties)
        .containsEntry("client.id", "camunda-control-k0")
        .containsEntry("max.block.ms", "8000");
  }

  @Test
  void inboundPropertiesRejectTheReportedExploit() {
    var additionalProperties = new HashMap<String, Object>();
    additionalProperties.put("config.providers", "env");
    additionalProperties.put(
        "config.providers.env.class",
        "org.apache.kafka.common.config.provider.EnvVarConfigProvider");
    additionalProperties.put("client.id", "K2ENV=${env:HOSTNAME}");
    var properties =
        new KafkaConnectorProperties(
            null,
            null,
            TOPIC,
            "group",
            additionalProperties,
            null,
            KafkaConnectorProperties.AutoOffsetReset.LATEST,
            null);

    assertThatThrownBy(() -> KafkaPropertyTransformer.getKafkaProperties(properties, null))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("config.providers");
  }
}
