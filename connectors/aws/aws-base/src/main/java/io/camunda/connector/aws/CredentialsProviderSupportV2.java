/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.*;

public class CredentialsProviderSupportV2 {

  private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsProviderSupportV2.class);

  public static AwsCredentialsProvider credentialsProvider(AwsBaseRequest request) {
    AwsAuthentication authentication = request.getAuthentication();
    if (authentication instanceof AwsAuthentication.AwsStaticCredentialsAuthentication sca) {
      LOGGER.debug("Using StaticCredentialsProvider for AWS authentication (using aws sdk v2)");
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(sca.accessKey(), sca.secretKey()));
    }
    LOGGER.debug("Falling to DefaultCredentialsProvider for AWS authentication (using aws sdk v2)");
    return DefaultCredentialsProvider.create();
  }
}
