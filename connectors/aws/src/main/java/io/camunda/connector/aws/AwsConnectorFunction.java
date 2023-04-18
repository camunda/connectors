/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.model.impl.AwsBaseAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;

@OutboundConnector(
    name = "AWS",
    inputVariables = {"authentication", "configuration", "input", "service"},
    type = "io.camunda:aws:1")
public class AwsConnectorFunction implements OutboundConnectorFunction {
  private final Gson gson;

  public AwsConnectorFunction() {
    this.gson = GsonComponentSupplier.gsonInstance();
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws Exception {
    String variables = context.getVariables();

    AwsBaseRequest awsRequest = gson.fromJson(variables, AwsBaseRequest.class);
    context.validate(awsRequest);
    context.replaceSecrets(awsRequest);

    return awsRequest
        .getService()
        .invoke(extractCredentialsProvider(awsRequest), awsRequest.getConfiguration(), context);
  }

  private AWSStaticCredentialsProvider extractCredentialsProvider(final AwsBaseRequest authConfig) {
    AwsBaseAuthentication authentication = authConfig.getAuthentication();
    return new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(authentication.getAccessKey(), authentication.getSecretKey()));
  }
}
