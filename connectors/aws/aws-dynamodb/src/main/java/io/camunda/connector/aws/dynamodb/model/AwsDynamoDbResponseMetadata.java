/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.awscore.AwsResponseMetadata;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * The {@code sdkResponseMetadata}/{@code sdkHttpMetadata} fragment shared by every item-write
 * operation's connector-owned result ({@link AddItemResult}, {@link DeleteItemResult}, {@link
 * UpdateItemResult}). Mirrors the identical fragment in aws-eventbridge's {@code
 * EventBridgeResult}.
 */
public final class AwsDynamoDbResponseMetadata {

  private AwsDynamoDbResponseMetadata() {}

  public record SdkResponseMetadata(String requestId) {
    // v2 returns the literal "UNKNOWN" when AWS_REQUEST_ID is absent; v1 exposed null there
    private static final String UNKNOWN_REQUEST_ID = "UNKNOWN";

    public static SdkResponseMetadata from(final AwsResponseMetadata metadata) {
      if (metadata == null) {
        return null;
      }
      String requestId = metadata.requestId();
      return new SdkResponseMetadata(UNKNOWN_REQUEST_ID.equals(requestId) ? null : requestId);
    }
  }

  @JsonPropertyOrder({"httpHeaders", "httpStatusCode", "allHttpHeaders"})
  public record SdkHttpMetadata(
      Map<String, String> httpHeaders,
      Integer httpStatusCode,
      Map<String, List<String>> allHttpHeaders) {

    public static SdkHttpMetadata from(final SdkHttpResponse httpResponse) {
      if (httpResponse == null) {
        return null;
      }
      Map<String, List<String>> allHttpHeaders = new LinkedHashMap<>(httpResponse.headers());
      Map<String, String> httpHeaders = new LinkedHashMap<>();
      allHttpHeaders.forEach(
          (name, values) ->
              httpHeaders.put(name, values == null || values.isEmpty() ? null : values.get(0)));
      return new SdkHttpMetadata(httpHeaders, httpResponse.statusCode(), allHttpHeaders);
    }
  }
}
