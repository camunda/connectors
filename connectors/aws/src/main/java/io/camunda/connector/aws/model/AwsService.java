/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;

public interface AwsService {

  String getType();

  void setType(final String type);

  Object invoke(
      final AWSStaticCredentialsProvider credentialsProvider,
      final AwsBaseConfiguration configuration,
      final OutboundConnectorContext context)
      throws InterruptedException;
}
