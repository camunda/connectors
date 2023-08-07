/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature.strategy;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public final class URLAndParametersEncodingStrategy implements HMACEncodingStrategy {
  @Override
  public byte[] getBytesToSign(final WebhookProcessingPayload payload) {
    return (payload.requestURL() + "?" + extractSignatureData(payload.params())).getBytes();
  }

  private static String extractSignatureData(final Map<String, String> params) {
    return params.entrySet().stream()
        .map(
            entry ->
                String.format(
                    "%s=%s",
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8),
                    URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)))
        .collect(Collectors.joining("&"));
  }
}
