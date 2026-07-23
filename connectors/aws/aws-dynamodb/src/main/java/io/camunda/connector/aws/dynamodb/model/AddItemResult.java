/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResponseMetadata.SdkHttpMetadata;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResponseMetadata.SdkResponseMetadata;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

/**
 * Connector-owned result of an {@code addItem} (AWS SDK v2 {@code PutItem}) call, reproducing the
 * exact JSON shape the v1 Document API's {@code PutItemOutcome} used to produce (see
 * AddItemOperationTest's golden-JSON test): {@code {"item": ..., "putItemResult": {...}}}.
 *
 * <p>This connector never requests {@code ReturnValues}/{@code ReturnConsumedCapacity}/{@code
 * ReturnItemCollectionMetrics}, so {@code item}/{@code attributes}/{@code consumedCapacity}/{@code
 * itemCollectionMetrics} are always {@code null} in practice; {@code consumedCapacity} and {@code
 * itemCollectionMetrics} are hardcoded {@code null} rather than passed through from the raw v2
 * response so that a future change can never silently reintroduce the "raw fluent-accessor-only SDK
 * object serializes to {}" hazard this migration is fixing.
 */
@JsonPropertyOrder({"item", "putItemResult"})
public record AddItemResult(Object item, PutItemResultEnvelope putItemResult) {

  public static AddItemResult from(final PutItemResponse response) {
    Object attributes = mapAttributes(response.attributes());
    return new AddItemResult(attributes, PutItemResultEnvelope.from(response, attributes));
  }

  private static Object mapAttributes(final Map<String, AttributeValue> attributes) {
    return (attributes == null || attributes.isEmpty())
        ? null
        : AttributeValueConverter.toPlainMap(attributes);
  }

  @JsonPropertyOrder({
    "sdkResponseMetadata",
    "sdkHttpMetadata",
    "attributes",
    "consumedCapacity",
    "itemCollectionMetrics"
  })
  public record PutItemResultEnvelope(
      SdkResponseMetadata sdkResponseMetadata,
      SdkHttpMetadata sdkHttpMetadata,
      Object attributes,
      Object consumedCapacity,
      Object itemCollectionMetrics) {

    static PutItemResultEnvelope from(final PutItemResponse response, final Object attributes) {
      return new PutItemResultEnvelope(
          SdkResponseMetadata.from(response.responseMetadata()),
          SdkHttpMetadata.from(response.sdkHttpResponse()),
          attributes,
          null,
          null);
    }
  }
}
