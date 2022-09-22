/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.suppliers;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

public class SqsClientSupplier {

  public AmazonSQS sqsClient(final String accessKey, final String secretKey, final String region) {
    return AmazonSQSClientBuilder.standard()
        .withCredentials(
            new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
        .withRegion(region)
        .build();
  }
}
