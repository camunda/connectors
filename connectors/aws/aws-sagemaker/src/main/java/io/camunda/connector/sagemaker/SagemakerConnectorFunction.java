/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.sagemaker.caller.SageMakerAsyncCaller;
import io.camunda.connector.sagemaker.caller.SageMakerSyncCaller;
import io.camunda.connector.sagemaker.model.SageMakerAsyncResponse;
import io.camunda.connector.sagemaker.model.SageMakerInvocationType;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import io.camunda.connector.sagemaker.model.SageMakerSyncResponse;
import io.camunda.connector.sagemaker.suppliers.SageMakeClientSupplier;
import java.util.function.BiFunction;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeAsyncClient;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

@OutboundConnector(
    name = "AWS SageMaker",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-sagemaker:1")
@ElementTemplate(
    engineVersion = "^8.6",
    id = "io.camunda.connectors.AWSSAGEMAKER.v1",
    name = "AWS SageMaker Outbound Connector",
    description = "Run inferences using AWS SageMaker.",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {
              "run inference",
              "perform asynchronous inference",
              "perform real-time inference"
            }),
    inputDataClass = SageMakerRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Configure input")
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.6/components/connectors/out-of-the-box-connectors/amazon-sagemaker/",
    icon = "icon.svg")
public class SagemakerConnectorFunction implements OutboundConnectorFunction {

  private final SageMakeClientSupplier sageMakeClientSupplier;
  private final BiFunction<SageMakerRuntimeClient, SageMakerRequest, SageMakerSyncResponse>
      syncCallerFunction;
  private final BiFunction<SageMakerRuntimeAsyncClient, SageMakerRequest, SageMakerAsyncResponse>
      asyncCallerFunction;

  public SagemakerConnectorFunction() {
    this(
        new SageMakeClientSupplier(),
        SageMakerSyncCaller.SYNC_REQUEST,
        SageMakerAsyncCaller.ASYNC_CALLER);
  }

  public SagemakerConnectorFunction(
      final SageMakeClientSupplier sageMakeClientSupplier,
      final BiFunction<SageMakerRuntimeClient, SageMakerRequest, SageMakerSyncResponse>
          syncCallerFunction,
      final BiFunction<SageMakerRuntimeAsyncClient, SageMakerRequest, SageMakerAsyncResponse>
          asyncCallerFunction) {
    this.sageMakeClientSupplier = sageMakeClientSupplier;
    this.syncCallerFunction = syncCallerFunction;
    this.asyncCallerFunction = asyncCallerFunction;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var request = context.bindVariables(SageMakerRequest.class);
    if (request.getInput().invocationType() == SageMakerInvocationType.ASYNC) {
      return asyncCallerFunction.apply(
          sageMakeClientSupplier.getAsyncClient(
              CredentialsProviderSupport.credentialsProvider(request),
              request.getConfiguration().region()),
          request);
    } else {
      return syncCallerFunction.apply(
          sageMakeClientSupplier.getSyncClient(
              CredentialsProviderSupport.credentialsProvider(request),
              request.getConfiguration().region()),
          request);
    }
  }
}
