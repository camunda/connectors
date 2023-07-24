/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.impl.feel.FEEL;
import io.camunda.connector.kafka.outbound.model.KafkaAuthentication;
import io.camunda.connector.kafka.outbound.model.KafkaTopic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KafkaConnectorProperties {

  @NotNull private String authenticationType;

  @Valid private KafkaAuthentication authentication;

  @Valid @NotNull private KafkaTopic topic;

  private Map<String, Object> additionalProperties = new HashMap<>();

  private String activationCondition;

  @FEEL private List<Long> offsets;

  @NotNull private AutoOffsetReset autoOffsetReset = AutoOffsetReset.NONE;

  private String groupId;

  public enum AutoOffsetReset {
    @JsonProperty("none")
    NONE("none"),
    @JsonProperty("latest")
    LATEST("latest"),
    @JsonProperty("earliest")
    EARLIEST("earliest");

    public final String label;

    AutoOffsetReset(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
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

  public List<Long> getOffsets() {
    return offsets;
  }

  public void setOffsets(List<Long> offsets) {
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

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
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
        + ", autoOffsetReset="
        + autoOffsetReset
        + ", groupId='"
        + groupId
        + '\''
        + '}';
  }
}
