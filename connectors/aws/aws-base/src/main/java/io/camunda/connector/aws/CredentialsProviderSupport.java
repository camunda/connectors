/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import static io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialsProviderSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsProviderSupport.class);

  public static AWSCredentialsProvider credentialsProvider(AwsBaseRequest request) {
    AwsAuthentication authentication = request.getAuthentication();
    if (authentication instanceof AwsStaticCredentialsAuthentication sca) {
      LOGGER.debug("Using AwsStaticCredentialsAuthentication for AWS authentication");
      return new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(sca.accessKey(), sca.secretKey()));
    }
    LOGGER.debug("Falling to DefaultAWSCredentialsProviderChain for AWS authentication");
    return new DefaultAWSCredentialsProviderChain();
  }
}
