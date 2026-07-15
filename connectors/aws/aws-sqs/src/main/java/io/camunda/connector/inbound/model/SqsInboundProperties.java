/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class SqsInboundProperties extends AwsBaseRequest {

  @TemplateProperty(
      id = "awsCredential",
      label = "AWS credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "awsCredential"),
      description =
          "Choose a reusable AWS credential. When set, it is bound as a whole to the connector's"
              + " 'awsCredential' input.")
  private AwsCredentialConfiguration awsCredential;

  public AwsCredentialConfiguration getAwsCredential() {
    return awsCredential;
  }

  public void setAwsCredential(AwsCredentialConfiguration awsCredential) {
    this.awsCredential = awsCredential;
  }

  /**
   * Per-connector consumption of the bound AWS credential: when a credential (configuration) is
   * bound, its authentication takes precedence over the inline authentication; inline is the
   * fallback.
   */
  @Override
  public AwsAuthentication getAuthentication() {
    return awsCredential != null ? awsCredential.authentication() : super.getAuthentication();
  }

  /**
   * When a credential is bound, its region drives the configuration; the inline endpoint (if any)
   * is preserved.
   */
  @Override
  public AwsBaseConfiguration getConfiguration() {
    if (awsCredential == null) {
      return super.getConfiguration();
    }
    String endpoint = super.getConfiguration() != null ? super.getConfiguration().endpoint() : null;
    return new AwsBaseConfiguration(awsCredential.region(), endpoint);
  }

  @Valid @NotNull private SqsInboundQueueProperties queue;

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
    return Objects.equals(queue, that.queue) && Objects.equals(awsCredential, that.awsCredential);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), queue, awsCredential);
  }

  @Override
  public String toString() {
    return "SqsInboundProperties{" + "queue=" + queue + ", awsCredential=" + awsCredential + "}";
  }
}
