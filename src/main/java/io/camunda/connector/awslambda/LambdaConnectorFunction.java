/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.awslambda.model.AwsLambdaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AWSLambda",
    inputVariables = {"authentication", "awsFunction"},
    type = "io.camunda:aws-lambda:1")
public class LambdaConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(LambdaConnectorFunction.class);
  private final AwsLambdaSupplier awsLambdaSupplier;
  private final Gson gson;

  public LambdaConnectorFunction() {
    awsLambdaSupplier = new AwsLambdaSupplier();
    gson = new GsonBuilder().create();
  }

  public LambdaConnectorFunction(final AwsLambdaSupplier awsLambdaSupplier, final Gson gson) {
    this.awsLambdaSupplier = awsLambdaSupplier;
    this.gson = gson;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.getVariablesAsType(AwsLambdaRequest.class);
    LOGGER.info("Executing my connector with request {}", request);
    context.validate(request);
    context.replaceSecrets(request);
    return new AwsLambdaResult(invokeLambdaFunction(request), gson);
  }

  private InvokeResult invokeLambdaFunction(AwsLambdaRequest request) {
    final AWSLambda awsLambda =
        awsLambdaSupplier.awsLambdaService(
            request.getAuthentication(), request.getAwsFunction().getRegion());
    final InvokeRequest invokeRequest =
        new InvokeRequest()
            .withFunctionName(request.getAwsFunction().getFunctionName())
            .withPayload(gson.toJson(request.getAwsFunction().getPayload()));
    try {
      return awsLambda.invoke(invokeRequest);
    } finally {
      if (awsLambda != null) {
        awsLambda.shutdown();
      }
    }
  }
}
