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
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;

/**
 * Connector-owned result of a {@code deleteItem} (AWS SDK v2 {@code DeleteItem}) call, reproducing
 * the exact JSON shape the v1 Document API's {@code DeleteItemOutcome} used to produce (see
 * DeleteItemOperationTest's golden-JSON test): {@code {"item": ..., "deleteItemResult": {...}}}.
 * See {@link AddItemResult} for why {@code consumedCapacity}/{@code itemCollectionMetrics} are
 * hardcoded {@code null}.
 */
@JsonPropertyOrder({"item", "deleteItemResult"})
public record DeleteItemResult(Object item, DeleteItemResultEnvelope deleteItemResult) {

  public static DeleteItemResult from(final DeleteItemResponse response) {
    Object attributes = mapAttributes(response.attributes());
    return new DeleteItemResult(attributes, DeleteItemResultEnvelope.from(response, attributes));
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
  public record DeleteItemResultEnvelope(
      SdkResponseMetadata sdkResponseMetadata,
      SdkHttpMetadata sdkHttpMetadata,
      Object attributes,
      Object consumedCapacity,
      Object itemCollectionMetrics) {

    static DeleteItemResultEnvelope from(
        final DeleteItemResponse response, final Object attributes) {
      return new DeleteItemResultEnvelope(
          SdkResponseMetadata.from(response.responseMetadata()),
          SdkHttpMetadata.from(response.sdkHttpResponse()),
          attributes,
          null,
          null);
    }
  }
}
