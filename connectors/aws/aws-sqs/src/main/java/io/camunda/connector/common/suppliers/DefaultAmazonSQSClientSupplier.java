/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

public class DefaultAmazonSQSClientSupplier implements AmazonSQSClientSupplier {

  public AmazonSQS sqsClient(
      final AWSCredentialsProvider credentialsProvider, final String region) {
    return AmazonSQSClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build();
  }
}
