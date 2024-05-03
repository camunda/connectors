/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker;

import static org.mockito.ArgumentMatchers.any;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeAsync;
import io.camunda.connector.sagemaker.suppliers.SageMakeClientSupplier;
import io.camunda.connector.sagemaker.testutils.SageMakerTestUtils;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.util.function.BiFunction;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagemakerConnectorFunctionTest {

  @Test
  void executeSyncRequest() {
    var context =
        OutboundConnectorContextBuilder.create()
            .secret("ACCESS_KEY", SageMakerTestUtils.ACTUAL_ACCESS_KEY)
            .secret("SECRET_KEY", SageMakerTestUtils.ACTUAL_SECRET_KEY)
            .variables(SageMakerTestUtils.REAL_TIME_EXECUTION_JSON)
            .build();

    var syncRuntime = Mockito.mock(AmazonSageMakerRuntime.class);

    var sageMakeClientSupplier = Mockito.mock(SageMakeClientSupplier.class);
    Mockito.when(
            sageMakeClientSupplier.getSyncClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(syncRuntime);

    var syncCallerFunction = Mockito.mock(BiFunction.class);
    var executionResult = new Object();
    Mockito.when(syncCallerFunction.apply(any(), any())).thenReturn(executionResult);

    var asyncCallerFunction = Mockito.mock(BiFunction.class);

    var connector =
        new SagemakerConnectorFunction(
            sageMakeClientSupplier, syncCallerFunction, asyncCallerFunction);

    var result = connector.execute(context);

    Mockito.verify(syncCallerFunction).apply(any(), any());
    Mockito.verify(asyncCallerFunction, Mockito.never()).apply(any(), any());
    Assertions.assertThat(result).isSameAs(executionResult);
  }

  @Test
  void executeAsyncRequest() {
    var context =
        OutboundConnectorContextBuilder.create()
            .secret("ACCESS_KEY", SageMakerTestUtils.ACTUAL_ACCESS_KEY)
            .secret("SECRET_KEY", SageMakerTestUtils.ACTUAL_SECRET_KEY)
            .variables(SageMakerTestUtils.ASYNC_EXECUTION_JSON)
            .build();
    var asyncRuntime = Mockito.mock(AmazonSageMakerRuntimeAsync.class);

    var sageMakeClientSupplier = Mockito.mock(SageMakeClientSupplier.class);
    Mockito.when(
            sageMakeClientSupplier.getAsyncClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(asyncRuntime);

    var syncCallerFunction = Mockito.mock(BiFunction.class);

    var asyncCallerFunction = Mockito.mock(BiFunction.class);
    var executionResult = new Object();
    Mockito.when(asyncCallerFunction.apply(any(), any())).thenReturn(executionResult);

    var connector =
        new SagemakerConnectorFunction(
            sageMakeClientSupplier, syncCallerFunction, asyncCallerFunction);

    var result = connector.execute(context);

    Mockito.verify(syncCallerFunction, Mockito.never()).apply(any(), any());
    Mockito.verify(asyncCallerFunction).apply(any(), any());
    Assertions.assertThat(result).isSameAs(executionResult);
  }
}
