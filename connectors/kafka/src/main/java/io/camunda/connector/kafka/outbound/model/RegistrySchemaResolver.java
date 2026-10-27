/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import io.camunda.connector.kafka.model.SchemaSource;
import io.camunda.connector.kafka.model.schema.OutboundSchemaRegistryStrategy;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class RegistrySchemaResolver {

  private static final int CLIENT_CACHE_CAPACITY = 100;

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
    var configs = new HashMap<String, Object>();
    Optional.ofNullable(request.additionalProperties()).ifPresent(configs::putAll);
    var client = clientFactory.apply(strategy.getSchemaRegistryUrl(), configs);
    int id = schemaId(client, strategy, subject);
    return Optional.of(
        new ResolvedSchema(subject, id, client.getSchemaBySubjectAndId(subject, id)));
  }

  public static String subject(OutboundSchemaRegistryStrategy strategy, String topic) {
    var subject = strategy.getSubject();
    return subject == null || subject.isBlank() ? topic + "-value" : subject;
  }

  private int schemaId(
      SchemaRegistryClient client, OutboundSchemaRegistryStrategy strategy, String subject)
      throws Exception {
    if (strategy.getSchemaId() != null) {
      return strategy.getSchemaId();
    }
    if (strategy.getSchemaVersion() != null) {
      return client.getSchemaMetadata(subject, strategy.getSchemaVersion()).getId();
    }
    return client.getLatestSchemaMetadata(subject).getId();
  }
}
