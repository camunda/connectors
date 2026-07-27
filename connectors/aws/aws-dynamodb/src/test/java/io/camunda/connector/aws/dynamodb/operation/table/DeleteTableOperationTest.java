/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.DeleteTable;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

class DeleteTableOperationTest extends BaseDynamoDbOperationTest {

  @Mock private DynamoDbWaiter waiter;

  @BeforeEach
  public void init() {
    when(dynamoDbClient.waiter()).thenReturn(waiter);
  }

  @Test
  public void invoke_shouldDeleteDynamoDbTableAndReturnStatusOk() {
    // Given
    DeleteTable deleteTable = new DeleteTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    DeleteTableOperation operation = new DeleteTableOperation(deleteTable);
    // When
    Object invoke = operation.invoke(dynamoDbClient);
    // Then
    verify(dynamoDbClient, times(1))
        .deleteTable(
            DeleteTableRequest.builder()
                .tableName(TestDynamoDBData.ActualValue.TABLE_NAME)
                .build());
    verify(waiter, times(1))
        .waitUntilTableNotExists(
            any(DescribeTableRequest.class), any(WaiterOverrideConfiguration.class));

    assertThat(invoke).isNotNull();
    AwsDynamoDbResult result = (AwsDynamoDbResult) invoke;
    assertThat(result.getAction())
        .isEqualTo("delete Table [" + TestDynamoDBData.ActualValue.TABLE_NAME + "]");
    assertThat(result.getStatus()).isEqualTo("OK");
  }

  /**
   * Regression guard for the waiter backoff: the v2 DynamoDbWaiter's TableNotExists default poll
   * delay is a fixed 20s backoff, and its override merge reads only the caller's {@code
   * backoffStrategyV2}, so {@code maxAttempts} alone would leave 20s spacing (only ~7 checks). The
   * operation must set an explicit 5s fixed backoff to reproduce v1's 25 x 5s behavior, with {@code
   * maxAttempts} as the sole binding constraint (no wall-clock cap).
   */
  @Test
  public void invoke_appliesFixedFiveSecondWaiterBackoff() {
    DeleteTable deleteTable = new DeleteTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    DeleteTableOperation operation = new DeleteTableOperation(deleteTable);

    operation.invoke(dynamoDbClient);

    ArgumentCaptor<WaiterOverrideConfiguration> configCaptor =
        ArgumentCaptor.forClass(WaiterOverrideConfiguration.class);
    verify(waiter).waitUntilTableNotExists(any(DescribeTableRequest.class), configCaptor.capture());
    WaiterOverrideConfiguration config = configCaptor.getValue();

    assertThat(config.maxAttempts()).contains(25);
    assertThat(config.waitTimeout()).isEmpty();
    assertThat(config.backoffStrategyV2()).isPresent();
    assertThat(config.backoffStrategyV2().get().computeDelay(1)).isEqualTo(Duration.ofSeconds(5));
    assertThat(config.backoffStrategyV2().get().computeDelay(10)).isEqualTo(Duration.ofSeconds(5));
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 deleteTable operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). {@link AwsDynamoDbResult} is a connector-owned envelope (not a raw AWS SDK type),
   * but deleteTable never sets its {@code response} field -- pinning that it serializes as an
   * explicit JSON null, not an absent key.
   */
  @Test
  public void deleteTable_serializesToDocumentedV1JsonShape() throws Exception {
    DeleteTable deleteTable = new DeleteTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    DeleteTableOperation operation = new DeleteTableOperation(deleteTable);

    Object result = operation.invoke(dynamoDbClient);

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "action": "delete Table [my_table]",
          "status": "OK",
          "response": null
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() {
    // Given
    String input =
        """
                {
                  "type": "deleteTable",
                  "tableName": "{{secrets.TABLE_NAME_KEY}}"
                }
                """;
    OutboundConnectorContext context = getContextWithSecrets(input);
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(DeleteTable.class);
    DeleteTable castedRequest = (DeleteTable) request;
    assertThat(castedRequest.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
  }
}
