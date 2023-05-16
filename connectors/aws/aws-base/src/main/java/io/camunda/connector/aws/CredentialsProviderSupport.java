/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import io.camunda.connector.aws.model.impl.AwsBaseAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;

public class CredentialsProviderSupport {

  public static AWSCredentialsProvider credentialsProvider(AwsBaseRequest request) {
    AwsBaseAuthentication authentication = request.getAuthentication();
    return new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(authentication.getAccessKey(), authentication.getSecretKey()));
  }
}
