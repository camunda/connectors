/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class SnsConnectorRequest extends AwsBaseRequest {

  @Valid @NotNull @Secret private TopicRequestData topic;

  public TopicRequestData getTopic() {
    return topic;
  }

  public void setTopic(final TopicRequestData topic) {
    this.topic = topic;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final SnsConnectorRequest that)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(topic, that.topic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), topic);
  }

  @Override
  public String toString() {
    return "SnsConnectorRequest{" + "topic=" + topic + "}";
  }
}
