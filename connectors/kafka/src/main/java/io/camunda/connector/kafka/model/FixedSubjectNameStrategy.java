/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;
import java.util.Map;

/**
 * Makes the Confluent serializer use the subject the connector already resolved.
 *
 * <p>The connector looks up the schema in the registry under a subject: the one the user entered,
 * or {@code <topic>-value} if they left it empty. When the message is then serialized, the
 * Confluent serializer works out a subject on its own, by default {@code <topic>-value}, and checks
 * the schema ID against it. For a custom subject the two would differ, so the lookup and the
 * serialization would use different subjects. This strategy makes the serializer use the same
 * subject as the lookup.
 */
public class FixedSubjectNameStrategy implements SubjectNameStrategy {

  /**
   * Config key under which the resolved subject is handed to this class.
   *
   * <p>We cannot call {@code new FixedSubjectNameStrategy(subject)} ourselves: the serializer
   * creates the strategy from its class name, without arguments, and then calls {@link
   * #configure(Map)} with the producer config. That config is the only way to get a value in, so
   * the connector stores the subject under this key and {@link #configure(Map)} reads it back. The
   * key is only a name for that entry; it is never sent to the registry.
   *
   * <p>The config is built per request and every producer gets its own strategy instance, so
   * concurrent tasks with different subjects do not share this value.
   */
  public static final String SUBJECT_CONFIG = "camunda.connector.fixed.subject";

  private String subject;

  @Override
  public void configure(Map<String, ?> configs) {
    subject = (String) configs.get(SUBJECT_CONFIG);
  }

  @Override
  public String subjectName(String topic, boolean isKey, ParsedSchema schema) {
    return subject;
  }
}
