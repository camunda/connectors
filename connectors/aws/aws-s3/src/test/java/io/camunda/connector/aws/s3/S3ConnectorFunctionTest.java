/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.aws.s3.core.S3Executor;
import io.camunda.connector.aws.s3.model.response.DeleteResponse;
import io.camunda.connector.aws.s3.model.response.DownloadResponse;
import io.camunda.connector.aws.s3.model.response.UploadResponse;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class S3ConnectorFunctionTest extends BaseTest {

  @ParameterizedTest
  @MethodSource("loadUploadActionVariables")
  void executeUploadActionReturnsCorrectResult(String variables) {

    var s3ConnectorFunction = new S3ConnectorFunction();
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var s3Executor = Mockito.mock(S3Executor.class);

    try (MockedStatic<S3Executor> s3ExecutorMockedStatic = Mockito.mockStatic(S3Executor.class)) {
      s3ExecutorMockedStatic.when(() -> S3Executor.create(any(), any())).thenReturn(s3Executor);
      when(s3Executor.execute(any())).thenReturn(new UploadResponse("test", "test", "link"));
      var response = s3ConnectorFunction.execute(context);
      Assertions.assertNotNull(response);
      Assertions.assertInstanceOf(UploadResponse.class, response);
    }
  }

  @ParameterizedTest
  @MethodSource("loadDownloadActionVariables")
  void executeDownloadActionReturnsCorrectResult(String variables) {

    var s3ConnectorFunction = new S3ConnectorFunction();
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var s3Executor = Mockito.mock(S3Executor.class);

    try (MockedStatic<S3Executor> s3ExecutorMockedStatic = Mockito.mockStatic(S3Executor.class)) {
      s3ExecutorMockedStatic.when(() -> S3Executor.create(any(), any())).thenReturn(s3Executor);
      when(s3Executor.execute(any())).thenReturn(new DownloadResponse("test", "test", null));
      var response = s3ConnectorFunction.execute(context);
      Assertions.assertNotNull(response);
      Assertions.assertInstanceOf(DownloadResponse.class, response);
    }
  }

  @ParameterizedTest
  @MethodSource("loadDeleteActionVariables")
  void executeDeleteActionReturnsCorrectResult(String variables) {

    var s3ConnectorFunction = new S3ConnectorFunction();
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var s3Executor = Mockito.mock(S3Executor.class);

    try (MockedStatic<S3Executor> s3ExecutorMockedStatic = Mockito.mockStatic(S3Executor.class)) {
      s3ExecutorMockedStatic.when(() -> S3Executor.create(any(), any())).thenReturn(s3Executor);
      when(s3Executor.execute(any())).thenReturn(new DeleteResponse("test", "test"));
      var response = s3ConnectorFunction.execute(context);
      Assertions.assertNotNull(response);
      Assertions.assertInstanceOf(DeleteResponse.class, response);
    }
  }
}
