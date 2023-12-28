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
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.awslambda.model.AwsLambdaResult;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AWS Lambda",
    inputVariables = {"authentication", "configuration", "awsFunction"},
    type = "io.camunda:aws-lambda:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSLAMBDA.v2",
    name = "AWS Lambda Outbound Connector",
    description = "Invoke a function",
    inputDataClass = AwsLambdaRequest.class,
    version = 5,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Select operation"),
      @ElementTemplate.PropertyGroup(id = "operationDetails", label = "Operation details")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-lambda/",
    icon = "icon.svg")
public class LambdaConnectorFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(LambdaConnectorFunction.class); // todo delete

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
  public Object execute(OutboundConnectorContext context) {
    LOGGER.info("start executing connector"); // todo delete all this

    var request = context.bindVariables(AwsLambdaRequest.class);
    return new AwsLambdaResult(invokeLambdaFunction(request), objectMapper);
  }

  private InvokeResult invokeLambdaFunction(AwsLambdaRequest request) {
    LOGGER.info("invokeLambdaFunction"); // todo delete all this

    var region =
        AwsUtils.extractRegionOrDefault(
            request.getConfiguration(), request.getAwsFunction().getRegion());
    AWSLambda awsLambda = createAwsLambdaClient(request, region);
    LOGGER.info("REGION : {}", region); // todo delete all this
    LOGGER.info(
        "FUNCTION NAME : {}", request.getAwsFunction().getFunctionName()); // todo delete all this
    LOGGER.info("PAYLOAD : {}", request.getAwsFunction().getPayload()); // todo delete all this
    LOGGER.info("PAYLOAD : {}", request.getAwsFunction().getPayload()); // todo delete all this
    try {

      final InvokeRequest invokeRequest =
          new InvokeRequest()
              .withFunctionName(request.getAwsFunction().getFunctionName())
              .withPayload(objectMapper.writeValueAsString(request.getAwsFunction().getPayload()));
      return awsLambda.invoke(invokeRequest);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error mapping payload to json.");
    } finally {
      LOGGER.info("FINISH "); // todo delete all this
      if (awsLambda != null) {
        awsLambda.shutdown();
      }
    }
  }

  private AWSLambda createAwsLambdaClient(AwsLambdaRequest request, String region) {
    LOGGER.info("createAwsLambdaClient"); // todo delete all this

    Optional<String> endpoint =
        Optional.ofNullable(request.getConfiguration()).map(AwsBaseConfiguration::endpoint);
    LOGGER.info("ENDPOINT : {}", endpoint); // todo delete all this

    var credentialsProvider = CredentialsProviderSupport.credentialsProvider(request);
    return endpoint
        .map(ep -> awsLambdaSupplier.awsLambdaService(credentialsProvider, region, ep))
        .orElseGet(() -> awsLambdaSupplier.awsLambdaService(credentialsProvider, region));
  }
}
