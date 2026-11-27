/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.api.error.ConnectorInputException;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.security.auth.login.AppConfigurationEntry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.types.Password;
import org.apache.kafka.common.security.JaasContext;

/**
 * Checks the additional properties of a Kafka connector before they reach the Kafka client.
 *
 * <p>Additional properties come from the process model. Without a check they let the author of a
 * process make the client read local files and environment variables (config providers, OAuth token
 * and assertion files), run a command (Kerberos kinit), or load any class on the classpath by name.
 * This class rejects those properties and leaves ordinary client settings alone.
 */
public final class KafkaAdditionalPropertiesValidator {

  // Never allowed, whatever the value
  private static final List<String> DENIED_PREFIXES =
      List.of(
          "config.providers",
          "sasl.oauthbearer.",
          "sasl.kerberos.",
          "bearer.auth.uami.",
          "rule.executors",
          "rule.actions");

  // Name a class (or a list of classes) but are not of type CLASS, or belong to a config
  // definition that is not consulted below
  private static final Set<String> CLASS_VALUED_KEYS =
      Set.of(
          "interceptor.classes",
          "metric.reporters",
          "security.providers",
          "partition.assignment.strategy",
          "key.subject.name.strategy",
          "value.subject.name.strategy",
          "reference.subject.name.strategy",
          "context.name.strategy",
          "provenance.strategy",
          "json.key.type",
          "json.value.type",
          "specific.avro.key.type",
          "specific.avro.value.type");

  private static final List<String> CLASS_VALUED_SUFFIXES =
      List.of(".class", ".classes", ".serializer", ".deserializer");

  // Keys of type CLASS in the client and serializer versions on the classpath
  private static final Set<String> CLASS_TYPED_KEYS = classTypedKeys();

  // The only classes an additional property may name
  private static final String SAFE_CLASS_PREFIX = "org.apache.kafka.common.serialization.";
  private static final Set<String> SAFE_CLASSES =
      Set.of(
          "io.confluent.kafka.serializers.KafkaAvroSerializer",
          "io.confluent.kafka.serializers.KafkaAvroDeserializer",
          "io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer",
          "io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer",
          "io.confluent.kafka.serializers.subject.TopicNameStrategy",
          "io.confluent.kafka.serializers.subject.RecordNameStrategy",
          "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy",
          "org.apache.kafka.clients.producer.RoundRobinPartitioner",
          "org.apache.kafka.clients.producer.UniformStickyPartitioner",
          "org.apache.kafka.clients.producer.internals.DefaultPartitioner",
          "org.apache.kafka.clients.consumer.RangeAssignor",
          "org.apache.kafka.clients.consumer.RoundRobinAssignor",
          "org.apache.kafka.clients.consumer.StickyAssignor",
          "org.apache.kafka.clients.consumer.CooperativeStickyAssignor",
          // the default of metric.reporters
          "org.apache.kafka.common.metrics.JmxReporter");

  private static final Set<String> ALLOWED_LOGIN_MODULES =
      Set.of(
          "org.apache.kafka.common.security.plain.PlainLoginModule",
          "org.apache.kafka.common.security.scram.ScramLoginModule");

  // The schema registry client picks a credential provider by these names. The others fetch a
  // token from a configurable URL or load a class.
  private static final String BEARER_AUTH_SOURCE = "bearer.auth.credentials.source";
  private static final Set<String> ALLOWED_BEARER_AUTH_SOURCES =
      Set.of("STATIC_TOKEN", "OAUTHBEARER");

  private static final int MAX_KEY_LENGTH_IN_MESSAGE = 100;

  // Second line of defence, in the Kafka client itself (3.8 and later): with this system property
  // set to "none" the client refuses every config provider that a client configuration names.
  // A value set by the operator is left alone.
  static final String AUTOMATIC_CONFIG_PROVIDERS = "org.apache.kafka.automatic.config.providers";

  static {
    try {
      if (System.getProperty(AUTOMATIC_CONFIG_PROVIDERS) == null) {
        System.setProperty(AUTOMATIC_CONFIG_PROVIDERS, "none");
      }
    } catch (SecurityException e) {
      // the property check below still applies
    }
  }

  private KafkaAdditionalPropertiesValidator() {}

  /**
   * @throws ConnectorInputException if a property is not allowed
   */
  public static void validate(Map<String, ?> additionalProperties) {
    if (additionalProperties == null) {
      return;
    }
    for (Map.Entry<String, ?> property : additionalProperties.entrySet()) {
      validate(property.getKey(), property.getValue());
    }
  }

  private static void validate(Object rawKey, Object value) {
    if (!(rawKey instanceof String originalKey)) {
      throw notAllowed(String.valueOf(rawKey));
    }
    // The Kafka client matches keys exactly. Normalizing here only rejects more.
    String key = originalKey.trim().toLowerCase(Locale.ROOT);
    for (String prefix : DENIED_PREFIXES) {
      if (key.startsWith(prefix)) {
        throw notAllowed(originalKey);
      }
    }
    if (key.equals(SaslConfigs.SASL_JAAS_CONFIG)) {
      if (!isAllowedJaasConfig(value)) {
        throw notAllowed(originalKey);
      }
    } else if (key.equals(BEARER_AUTH_SOURCE)) {
      if (!(value instanceof String source)
          || !ALLOWED_BEARER_AUTH_SOURCES.contains(source.trim().toUpperCase(Locale.ROOT))) {
        throw notAllowed(originalKey);
      }
    } else if (isClassValued(key) && !namesOnlySafeClasses(value)) {
      throw notAllowed(originalKey);
    }
  }

  private static boolean isClassValued(String key) {
    if (CLASS_VALUED_KEYS.contains(key) || CLASS_TYPED_KEYS.contains(key)) {
      return true;
    }
    for (String suffix : CLASS_VALUED_SUFFIXES) {
      if (key.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean namesOnlySafeClasses(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof Class<?> type) {
      return isSafeClass(type.getName());
    }
    if (value instanceof String names) {
      // a list of classes is a comma separated string
      for (String name : names.split(",", -1)) {
        if (!name.isBlank() && !isSafeClass(name.trim())) {
          return false;
        }
      }
      return true;
    }
    if (value instanceof Collection<?> elements) {
      for (Object element : elements) {
        if (element instanceof Collection<?> || !namesOnlySafeClasses(element)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean isSafeClass(String name) {
    return SAFE_CLASSES.contains(name)
        || (name.startsWith(SAFE_CLASS_PREFIX)
            && name.indexOf('.', SAFE_CLASS_PREFIX.length()) < 0);
  }

  // Parsed by the Kafka client itself, so that this check and the client read the same module
  private static boolean isAllowedJaasConfig(Object value) {
    if (!(value instanceof String config)) {
      return false;
    }
    List<AppConfigurationEntry> entries;
    try {
      entries =
          JaasContext.loadClientContext(Map.of(SaslConfigs.SASL_JAAS_CONFIG, new Password(config)))
              .configurationEntries();
    } catch (RuntimeException e) {
      return false;
    }
    if (entries == null || entries.isEmpty()) {
      return false;
    }
    for (AppConfigurationEntry entry : entries) {
      if (!ALLOWED_LOGIN_MODULES.contains(entry.getLoginModuleName())) {
        return false;
      }
    }
    return true;
  }

  private static Set<String> classTypedKeys() {
    Set<String> keys = new HashSet<>();
    try {
      addClassTypedKeys(keys, ProducerConfig.configDef());
      addClassTypedKeys(keys, ConsumerConfig.configDef());
      addClassTypedKeys(keys, AbstractKafkaSchemaSerDeConfig.baseConfigDef());
    } catch (RuntimeException | LinkageError e) {
      // the fixed lists above still apply
    }
    return Set.copyOf(keys);
  }

  private static void addClassTypedKeys(Set<String> keys, ConfigDef definition) {
    for (ConfigDef.ConfigKey configKey : definition.configKeys().values()) {
      if (configKey.type == ConfigDef.Type.CLASS) {
        keys.add(configKey.name.toLowerCase(Locale.ROOT));
      }
    }
  }

  // The value is never part of the message: it can hold a credential
  private static ConnectorInputException notAllowed(String key) {
    String shown =
        key.length() > MAX_KEY_LENGTH_IN_MESSAGE
            ? key.substring(0, MAX_KEY_LENGTH_IN_MESSAGE) + "..."
            : key;
    return new ConnectorInputException(
        new IllegalArgumentException(
            "Kafka additional property '"
                + shown
                + "' is not allowed, or its value is not allowed for this property"));
  }
}
