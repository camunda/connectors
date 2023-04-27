/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.common.model.SqsAuthenticationRequestData;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class SqsInboundProperties {
  @Valid @NotNull @Secret private SqsAuthenticationRequestData authentication;
  @Valid @NotNull @Secret private SqsInboundQueueProperties queue;

  public SqsAuthenticationRequestData getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final SqsAuthenticationRequestData authentication) {
    this.authentication = authentication;
  }

  public SqsInboundQueueProperties getQueue() {
    return queue;
  }

  public void setQueue(final SqsInboundQueueProperties queue) {
    this.queue = queue;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SqsInboundProperties that = (SqsInboundProperties) o;
    return Objects.equals(authentication, that.authentication) && Objects.equals(queue, that.queue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, queue);
  }

  @Override
  public String toString() {
    return "SqsInboundProperties{" + "authentication=" + authentication + ", queue=" + queue + "}";
  }
}
