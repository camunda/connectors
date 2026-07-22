/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.awscore.AwsResponseMetadata;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingModeSummary;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputDescription;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseDynamoDbOperationTest {
  protected static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();
  @Mock protected DynamoDbClient dynamoDbClient;

  /**
   * Builds an {@link AwsResponseMetadata} the way a live AWS SDK v2 call would populate it (via the
   * {@code AWS_REQUEST_ID} entry in the raw metadata map the SDK's response unmarshaller attaches
   * to every response).
   */
  protected static AwsResponseMetadata buildSdkResponseMetadata(String requestId) {
    return DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", requestId));
  }

  /** Builds an {@link SdkHttpResponse} the way a live AWS SDK v2 call would populate it. */
  protected static SdkHttpResponse buildSdkHttpResponse(int statusCode) {
    return SdkHttpResponse.builder()
        .statusCode(statusCode)
        .putHeader("Content-Length", "85")
        .build();
  }

  /**
   * Builds a realistically populated {@link TableDescription}, as returned by a live {@code
   * CreateTable}/{@code DescribeTable} call: partition + sort key, provisioned throughput, billing
   * mode summary, and the handful of top-level scalar fields AWS always sets. All other (~15)
   * optional fields (replicas, stream settings, restore/archival summaries, ...) are left unset and
   * serialize as explicit JSON nulls under the production mapper.
   */
  protected static TableDescription buildRealisticTableDescription(String tableName) {
    return TableDescription.builder()
        .tableName(tableName)
        .tableStatus(TableStatus.ACTIVE)
        .creationDateTime(Instant.ofEpochMilli(1700000000000L))
        .keySchema(
            KeySchemaElement.builder()
                .attributeName(TestDynamoDBData.ActualValue.PARTITION_KEY)
                .keyType(KeyType.HASH)
                .build(),
            KeySchemaElement.builder()
                .attributeName(TestDynamoDBData.ActualValue.SORT_KEY)
                .keyType(KeyType.RANGE)
                .build())
        .attributeDefinitions(
            AttributeDefinition.builder()
                .attributeName(TestDynamoDBData.ActualValue.PARTITION_KEY)
                .attributeType(ScalarAttributeType.N)
                .build(),
            AttributeDefinition.builder()
                .attributeName(TestDynamoDBData.ActualValue.SORT_KEY)
                .attributeType(ScalarAttributeType.S)
                .build())
        .itemCount(0L)
        .tableSizeBytes(0L)
        .tableArn("arn:aws:dynamodb:us-east-1:123456789012:table/" + tableName)
        .provisionedThroughput(
            ProvisionedThroughputDescription.builder()
                .readCapacityUnits(TestDynamoDBData.ActualValue.READ_CAPACITY)
                .writeCapacityUnits(TestDynamoDBData.ActualValue.WRITE_CAPACITY)
                .build())
        .billingModeSummary(BillingModeSummary.builder().billingMode("PROVISIONED").build())
        .build();
  }

  public OutboundConnectorContext getContextWithSecrets(String variables) {
    return OutboundConnectorContextBuilder.create()
        .variables(variables)
        .secret(TestDynamoDBData.Secrets.TABLE_NAME, TestDynamoDBData.ActualValue.TABLE_NAME)
        .secret(TestDynamoDBData.Secrets.ITEM_KEY, TestDynamoDBData.ActualValue.ITEM_KEY)
        .secret(TestDynamoDBData.Secrets.ITEM_VALUE, TestDynamoDBData.ActualValue.ITEM_VALUE)
        .secret(
            TestDynamoDBData.Secrets.KEY_ATTRIBUTE_VALUE,
            TestDynamoDBData.ActualValue.KEY_ATTRIBUTE_VALUE)
        .secret(TestDynamoDBData.Secrets.PARTITION_KEY, TestDynamoDBData.ActualValue.PARTITION_KEY)
        .secret(TestDynamoDBData.Secrets.SORT_KEY, TestDynamoDBData.ActualValue.SORT_KEY)
        .secret(
            TestDynamoDBData.Secrets.FILTER_EXPRESSION,
            TestDynamoDBData.ActualValue.FILTER_EXPRESSION)
        .secret(
            TestDynamoDBData.Secrets.PROJECTION_EXPRESSION,
            TestDynamoDBData.ActualValue.PROJECTION_EXPRESSION)
        .secret(
            TestDynamoDBData.Secrets.EXPRESSION_ATTRIBUTE_NAME,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_NAME)
        .secret(
            TestDynamoDBData.Secrets.EXPRESSION_ATTRIBUTE_VALUE,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_VALUE)
        .build();
  }
}
