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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.awslambda.model.AwsLambdaResult;
import io.camunda.connector.impl.ConnectorInputException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AWSLambda",
    inputVariables = {"authentication", "configuration", "awsFunction"},
    type = "io.camunda:aws-lambda:1")
public class LambdaConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(LambdaConnectorFunction.class);
  private final AwsLambdaSupplier awsLambdaSupplier;
  private final ObjectMapper objectMapper;

  public LambdaConnectorFunction() {
    this(new AwsLambdaSupplier(), ObjectMapperSupplier.getMapperInstance());
  }

  public LambdaConnectorFunction(
      final AwsLambdaSupplier awsLambdaSupplier, final ObjectMapper objectMapper) {
    this.awsLambdaSupplier = awsLambdaSupplier;
    this.objectMapper = objectMapper;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws JsonProcessingException {
    var request = context.getVariablesAsType(AwsLambdaRequest.class);
    LOGGER.info("Executing my connector with request {}", request);
    context.validate(request);
    context.replaceSecrets(request);
    return new AwsLambdaResult(invokeLambdaFunction(request), objectMapper);
  }

  private InvokeResult invokeLambdaFunction(AwsLambdaRequest request)
      throws JsonProcessingException {
    var region =
        Optional.ofNullable(request.getConfiguration())
            .map(AwsBaseConfiguration::getRegion)
            .or(() -> Optional.ofNullable(request.getAwsFunction().getRegion()))
            .orElseThrow(
                () ->
                    new ConnectorInputException(
                        new RuntimeException(
                            "Found constraints violated while validating input: Region is missing.")));
    final AWSLambda awsLambda =
        awsLambdaSupplier.awsLambdaService(
            CredentialsProviderSupport.credentialsProvider(request), region);
    final InvokeRequest invokeRequest =
        new InvokeRequest()
            .withFunctionName(request.getAwsFunction().getFunctionName())
            .withPayload(objectMapper.writeValueAsString(request.getAwsFunction().getPayload()));
    try {
      return awsLambda.invoke(invokeRequest);
    } finally {
      if (awsLambda != null) {
        awsLambda.shutdown();
      }
    }
  }
}
