/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class SnsConnectorRequest {

  @Valid @NotNull @Secret private AuthenticationRequestData authentication;
  @Valid @NotNull @Secret private TopicRequestData topic;

  public AuthenticationRequestData getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final AuthenticationRequestData authentication) {
    this.authentication = authentication;
  }

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
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SnsConnectorRequest that = (SnsConnectorRequest) o;
    return authentication.equals(that.authentication) && topic.equals(that.topic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, topic);
  }

  @Override
  public String toString() {
    return "SnsConnectorRequest{" + "authentication=" + authentication + ", topic=" + topic + '}';
  }
}
