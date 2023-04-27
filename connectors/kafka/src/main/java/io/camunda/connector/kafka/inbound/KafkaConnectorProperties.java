/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.kafka.outbound.model.KafkaAuthentication;
import io.camunda.connector.kafka.outbound.model.KafkaTopic;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class KafkaConnectorProperties {

  @NotNull private String authenticationType;

  @Valid @Secret private KafkaAuthentication authentication;

  @Valid @NotNull @Secret private KafkaTopic topic;

  private Map<String, Object> additionalProperties = new HashMap<>();

  @Secret private String activationCondition;

  private Object offsets;

  @NotNull private AutoOffsetReset autoOffsetReset = AutoOffsetReset.NONE;

  public enum AutoOffsetReset {
    NONE("none"),
    LATEST("latest"),
    EARLIEST("earliest");

    public final String label;

    private AutoOffsetReset(String label) {
      this.label = label;
    }
  }

  public String getAuthenticationType() {
    return authenticationType;
  }

  public void setAuthenticationType(String authenticationType) {
    this.authenticationType = authenticationType;
  }

  public AutoOffsetReset getAutoOffsetReset() {
    return autoOffsetReset;
  }

  public Object getOffsets() {
    return offsets;
  }

  public void setOffsets(Object offsets) {
    this.offsets = offsets;
  }

  public void setAutoOffsetReset(AutoOffsetReset autoOffsetReset) {
    this.autoOffsetReset = autoOffsetReset;
  }

  public String getActivationCondition() {
    return activationCondition;
  }

  public void setActivationCondition(String activationCondition) {
    this.activationCondition = activationCondition;
  }

  public KafkaAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(KafkaAuthentication authentication) {
    this.authentication = authentication;
  }

  public KafkaTopic getTopic() {
    return topic;
  }

  public void setTopic(KafkaTopic topic) {
    this.topic = topic;
  }

  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @Override
  public String toString() {
    return "KafkaConnectorProperties{"
        + "authenticationType='"
        + authenticationType
        + '\''
        + ", authentication="
        + authentication
        + ", topic="
        + topic
        + ", additionalProperties="
        + additionalProperties
        + ", activationCondition='"
        + activationCondition
        + '\''
        + ", offsets="
        + offsets
        + ", autoOffsetReset='"
        + autoOffsetReset
        + '\''
        + '}';
  }
}
