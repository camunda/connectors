/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature.strategy;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.util.*;
import java.util.stream.Stream;

public final class URLAndBodyEncodingStrategy implements HMACEncodingStrategy {

  private static String extractSignatureData(final WebhookProcessingPayload payload) {
    if (payload.rawBody() == null || payload.rawBody().length == 0) {
      throw new NullPointerException(
          "Can't extract signature data from body, because body is null");
    }

    Object rawBody =
        HttpWebhookUtil.transformRawBodyToObject(
            payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers()));

    List<String> sortedKeys = convertToListOfString(rawBody);
    StringBuilder builder = new StringBuilder();
    sortedKeys.forEach(builder::append);
    return builder.toString();
  }

  private static List<String> convertToListOfString(Object o) {
    Map<String, String> result = new HashMap<>();
    if (o instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() instanceof String key) {
          result.put(key, entry.getValue() == null ? "" : (String) entry.getValue());
        }
      }
    } else if (o instanceof List<?> list) {
      for (int i = 0; i < list.size(); i = i + 2) {
        if (list.get(i) instanceof String key && i + 1 < list.size()) {
          result.put(key, list.get(i + 1) == null ? "" : (String) list.get(i + 1));
        }
      }
    }
    return result.keySet().stream().sorted().flatMap(s -> Stream.of(s, result.get(s))).toList();
  }

  @Override
  public byte[] getBytesToSign(final WebhookProcessingPayload payload) {
    return (payload.requestURL() + extractSignatureData(payload)).getBytes();
  }
}
