/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker;

import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeAsync;
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

@OutboundConnector(
    name = "AWS SageMaker",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-sagemaker:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSSAGEMAKER.v1",
    name = "AWS SageMaker Outbound Connector",
    description = "Execute SageMaker models",
    inputDataClass = SageMakerRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Configure input")
    },
    documentationRef =
        "https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/aws-sagemaker/",
    icon = "icon.svg")
public class SagemakerConnectorFunction implements OutboundConnectorFunction {

  private final SageMakeClientSupplier sageMakeClientSupplier;
  private final BiFunction<AmazonSageMakerRuntime, SageMakerRequest, SageMakerSyncResponse>
      syncCallerFunction;
  private final BiFunction<AmazonSageMakerRuntimeAsync, SageMakerRequest, SageMakerAsyncResponse>
      asyncCallerFunction;

  public SagemakerConnectorFunction() {
    this(
        new SageMakeClientSupplier(),
        SageMakerSyncCaller.SYNC_REQUEST,
        SageMakerAsyncCaller.ASYNC_CALLER);
  }

  public SagemakerConnectorFunction(
      final SageMakeClientSupplier sageMakeClientSupplier,
      final BiFunction<AmazonSageMakerRuntime, SageMakerRequest, SageMakerSyncResponse>
          syncCallerFunction,
      final BiFunction<AmazonSageMakerRuntimeAsync, SageMakerRequest, SageMakerAsyncResponse>
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
