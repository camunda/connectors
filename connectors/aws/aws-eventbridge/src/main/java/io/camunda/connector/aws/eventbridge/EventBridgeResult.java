/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.awscore.AwsResponseMetadata;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

/**
 * Connector-owned result of an EventBridge {@code PutEvents} call.
 *
 * <p>The AWS SDK v2 model classes ({@link PutEventsResponse} and friends) expose fluent accessors
 * ({@code entries()}, {@code failedEntryCount()}) rather than JavaBean getters. Serializing them
 * directly with the connectors' {@code ObjectMapper} (which disables {@code FAIL_ON_EMPTY_BEANS})
 * silently produced {@code {}}, dropping the partial-failure signal ({@code failedEntryCount}) and
 * the per-entry {@code eventId}. This record maps the v2 response back into the exact JSON shape
 * that the pre-v2 (AWS SDK v1) connector documented and returned, restoring that output contract.
 */
@JsonPropertyOrder({"sdkResponseMetadata", "sdkHttpMetadata", "failedEntryCount", "entries"})
public record EventBridgeResult(
    SdkResponseMetadata sdkResponseMetadata,
    SdkHttpMetadata sdkHttpMetadata,
    Integer failedEntryCount,
    List<Entry> entries) {

  /** Maps an AWS SDK v2 {@link PutEventsResponse} into the v1-shaped connector result. */
  public static EventBridgeResult from(final PutEventsResponse response) {
    return new EventBridgeResult(
        SdkResponseMetadata.from(response.responseMetadata()),
        SdkHttpMetadata.from(response.sdkHttpResponse()),
        response.failedEntryCount(),
        mapEntries(response.entries()));
  }

  private static List<Entry> mapEntries(
      final List<software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry> entries) {
    if (entries == null) {
      return null;
    }
    return entries.stream().map(Entry::from).toList();
  }

  public record SdkResponseMetadata(String requestId) {
    // v2 returns the literal "UNKNOWN" when AWS_REQUEST_ID is absent; v1 exposed null there
    private static final String UNKNOWN_REQUEST_ID = "UNKNOWN";

    static SdkResponseMetadata from(final AwsResponseMetadata metadata) {
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

    static SdkHttpMetadata from(final SdkHttpResponse httpResponse) {
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

  @JsonPropertyOrder({"eventId", "errorCode", "errorMessage"})
  public record Entry(String eventId, String errorCode, String errorMessage) {
    static Entry from(
        final software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry entry) {
      return new Entry(entry.eventId(), entry.errorCode(), entry.errorMessage());
    }
  }
}
