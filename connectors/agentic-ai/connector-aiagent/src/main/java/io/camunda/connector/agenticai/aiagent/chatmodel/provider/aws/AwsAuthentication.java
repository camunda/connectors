/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws;

/** How a call to AWS authenticates. */
public sealed interface AwsAuthentication {

  record StaticCredentials(String accessKey, String secretKey) implements AwsAuthentication {

    @Override
    public String toString() {
      return "StaticCredentials{accessKey=[REDACTED], secretKey=[REDACTED]}";
    }
  }

  /** A bearer token (for example a Bedrock API key) instead of signed requests. */
  record ApiKey(String apiKey) implements AwsAuthentication {

    @Override
    public String toString() {
      return "ApiKey{apiKey=[REDACTED]}";
    }
  }

  /** The default credentials chain of the AWS SDK. */
  record DefaultCredentialsChain() implements AwsAuthentication {}
}
