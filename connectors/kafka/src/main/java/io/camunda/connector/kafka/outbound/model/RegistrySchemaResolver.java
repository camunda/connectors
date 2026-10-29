/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.model.SchemaSource;
import io.camunda.connector.kafka.model.schema.OutboundSchemaRegistryStrategy;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class RegistrySchemaResolver {

  private static final int CLIENT_CACHE_CAPACITY = 100;
  private static final String SCHEMA_REGISTRY_URL = "schema.registry.url";

  private final BiFunction<String, Map<String, Object>, SchemaRegistryClient> clientFactory;

  public RegistrySchemaResolver() {
    this(
        (url, configs) ->
            new CachedSchemaRegistryClient(
                url,
                CLIENT_CACHE_CAPACITY,
                List.<SchemaProvider>of(new AvroSchemaProvider(), new JsonSchemaProvider()),
                configs));
  }

  public RegistrySchemaResolver(
      BiFunction<String, Map<String, Object>, SchemaRegistryClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  public Optional<ResolvedSchema> resolve(KafkaConnectorRequest request) throws Exception {
    if (!(request.schemaStrategy() instanceof OutboundSchemaRegistryStrategy strategy)
        || strategy.getSchemaSource() != SchemaSource.REGISTRY) {
      return Optional.empty();
    }
    var subject = subject(strategy, request.topic().topicName());
    // same effective settings as the producer, so the lookup and the serializer use the same
    // registry and credentials
    var props = KafkaPropertiesUtil.assembleKafkaClientProperties(request);
    var configs = new HashMap<String, Object>();
    props.forEach((key, value) -> configs.put(String.valueOf(key), value));
    var url = registryUrl(props, strategy);
    try (var client = clientFactory.apply(url, configs)) {
      int id = schemaId(client, strategy, subject);
      return Optional.of(
          new ResolvedSchema(subject, id, client.getSchemaBySubjectAndId(subject, id)));
    }
  }

  // the serializer accepts a single URL, a comma separated string or a list
  private static String registryUrl(Properties props, OutboundSchemaRegistryStrategy strategy) {
    var configured = props.getOrDefault(SCHEMA_REGISTRY_URL, strategy.getSchemaRegistryUrl());
    if (configured instanceof Collection<?> urls) {
      return urls.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
    return String.valueOf(configured);
  }

  public static String subject(OutboundSchemaRegistryStrategy strategy, String topic) {
    var subject = strategy.getSubject();
    return subject == null || subject.isBlank() ? topic + "-value" : subject;
  }

  private int schemaId(
      SchemaRegistryClient client, OutboundSchemaRegistryStrategy strategy, String subject)
      throws Exception {
    if (strategy.getSchemaId() != null) {
      int id = strategy.getSchemaId();
      if (!client.getAllSubjectsById(id).contains(subject)) {
        throw new IllegalArgumentException(
            "Schema ID " + id + " is not registered under subject " + subject);
      }
      return id;
    }
    if (strategy.getSchemaVersion() != null) {
      return client.getSchemaMetadata(subject, strategy.getSchemaVersion()).getId();
    }
    return client.getLatestSchemaMetadata(subject).getId();
  }
}
