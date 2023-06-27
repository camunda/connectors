/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class SqsInboundProperties extends AwsBaseRequest {
  @Valid @NotNull @Secret private SqsInboundQueueProperties queue;

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
    if (!(o instanceof final SqsInboundProperties that)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(queue, that.queue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), queue);
  }

  @Override
  public String toString() {
    return "SqsInboundProperties{" + "queue=" + queue + "}";
  }
}
