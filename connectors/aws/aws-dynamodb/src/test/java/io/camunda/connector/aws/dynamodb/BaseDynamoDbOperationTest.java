/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import static org.mockito.Mockito.when;

import com.amazonaws.ResponseMetadata;
import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingModeSummary;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseDynamoDbOperationTest {
  protected static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();
  @Mock protected DynamoDB dynamoDB;
  @Mock protected Table table;

  /**
   * Builds a {@link ResponseMetadata} the way a live AWS SDK v1 call would populate it via {@code
   * AmazonWebServiceResult#setSdkResponseMetadata}.
   */
  protected static ResponseMetadata buildSdkResponseMetadata(String requestId) {
    return new ResponseMetadata(Map.of(ResponseMetadata.AWS_REQUEST_ID, requestId));
  }

  /**
   * Builds a {@link SdkHttpMetadata} the way a live AWS SDK v1 call would populate it via {@code
   * AmazonWebServiceResult#setSdkHttpMetadata}. {@link SdkHttpMetadata}'s only public factory,
   * {@code SdkHttpMetadata.from(HttpResponse)}, requires a {@code com.amazonaws.http.HttpResponse}
   * that in turn requires an Apache HttpClient request object to construct; reflection avoids
   * pulling that transitive dependency into this test-only module just to build a fixture.
   */
  protected static SdkHttpMetadata buildSdkHttpMetadata(int statusCode)
      throws ReflectiveOperationException {
    var constructor = SdkHttpMetadata.class.getDeclaredConstructor(Map.class, Map.class, int.class);
    constructor.setAccessible(true);
    Map<String, String> httpHeaders = Map.of("Content-Length", "85");
    Map<String, List<String>> allHttpHeaders = Map.of("Content-Length", List.of("85"));
    return constructor.newInstance(httpHeaders, allHttpHeaders, statusCode);
  }

  /**
   * Builds a realistically populated {@link TableDescription}, as returned by a live {@code
   * CreateTable}/{@code DescribeTable} call: partition + sort key, provisioned throughput, billing
   * mode summary, and the handful of top-level scalar fields AWS always sets. All other (~15)
   * optional fields (replicas, stream settings, restore/archival summaries, ...) are left unset and
   * serialize as explicit JSON nulls under the production mapper.
   */
  protected static TableDescription buildRealisticTableDescription(String tableName) {
    return new TableDescription()
        .withTableName(tableName)
        .withTableStatus(TableStatus.ACTIVE)
        .withCreationDateTime(new Date(1700000000000L))
        .withKeySchema(
            new KeySchemaElement(TestDynamoDBData.ActualValue.PARTITION_KEY, KeyType.HASH),
            new KeySchemaElement(TestDynamoDBData.ActualValue.SORT_KEY, KeyType.RANGE))
        .withAttributeDefinitions(
            new AttributeDefinition(
                TestDynamoDBData.ActualValue.PARTITION_KEY, ScalarAttributeType.N),
            new AttributeDefinition(TestDynamoDBData.ActualValue.SORT_KEY, ScalarAttributeType.S))
        .withItemCount(0L)
        .withTableSizeBytes(0L)
        .withTableArn("arn:aws:dynamodb:us-east-1:123456789012:table/" + tableName)
        .withProvisionedThroughput(
            new ProvisionedThroughputDescription()
                .withReadCapacityUnits(TestDynamoDBData.ActualValue.READ_CAPACITY)
                .withWriteCapacityUnits(TestDynamoDBData.ActualValue.WRITE_CAPACITY))
        .withBillingModeSummary(new BillingModeSummary().withBillingMode("PROVISIONED"));
  }

  @BeforeEach
  public void beforeEach() {
    when(dynamoDB.getTable(TestDynamoDBData.ActualValue.TABLE_NAME)).thenReturn(table);
    when(table.describe())
        .thenReturn(new TableDescription().withTableName(TestDynamoDBData.ActualValue.TABLE_NAME));
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
