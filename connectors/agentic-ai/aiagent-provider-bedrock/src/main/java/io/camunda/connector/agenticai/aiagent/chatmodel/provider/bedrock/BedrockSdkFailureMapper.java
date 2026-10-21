/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailure;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailureMapper;
import java.util.Optional;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/** Recognizes the failures the AWS SDK reports for a response of the Bedrock Runtime API. */
public class BedrockSdkFailureMapper implements SdkFailureMapper {

  @Override
  public Optional<SdkFailure> map(Throwable failure) {
    if (failure instanceof AwsServiceException e) {
      return Optional.of(
          new SdkFailure(
              e.statusCode(),
              Optional.ofNullable(e.awsErrorDetails()).map(d -> d.errorCode()).orElse(null),
              Optional.ofNullable(e.awsErrorDetails())
                  .map(d -> d.errorMessage())
                  .orElseGet(e::getMessage),
              false));
    }
    return Optional.empty();
  }
}
