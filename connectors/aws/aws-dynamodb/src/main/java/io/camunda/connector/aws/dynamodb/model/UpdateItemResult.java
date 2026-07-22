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
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/**
 * Connector-owned result of an {@code updateItem} (AWS SDK v2 {@code UpdateItem}) call, reproducing
 * the exact JSON shape the v1 Document API's {@code UpdateItemOutcome} used to produce (see
 * UpdateItemOperationTest's golden-JSON test): {@code {"item": ..., "updateItemResult": {...}}}.
 * See {@link AddItemResult} for why {@code consumedCapacity}/{@code itemCollectionMetrics} are
 * hardcoded {@code null}.
 */
@JsonPropertyOrder({"item", "updateItemResult"})
public record UpdateItemResult(Object item, UpdateItemResultEnvelope updateItemResult) {

  public static UpdateItemResult from(final UpdateItemResponse response) {
    Object attributes = mapAttributes(response.attributes());
    return new UpdateItemResult(attributes, UpdateItemResultEnvelope.from(response, attributes));
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
  public record UpdateItemResultEnvelope(
      SdkResponseMetadata sdkResponseMetadata,
      SdkHttpMetadata sdkHttpMetadata,
      Object attributes,
      Object consumedCapacity,
      Object itemCollectionMetrics) {

    static UpdateItemResultEnvelope from(
        final UpdateItemResponse response, final Object attributes) {
      return new UpdateItemResultEnvelope(
          SdkResponseMetadata.from(response.responseMetadata()),
          SdkHttpMetadata.from(response.sdkHttpResponse()),
          attributes,
          null,
          null);
    }
  }
}
