/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature.strategy;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public final class URLAndBodyEncodingStrategy implements HMACEncodingStrategy {

  private static String extractSignatureData(final WebhookProcessingPayload payload) {
    if (payload.rawBody() == null || payload.rawBody().length == 0) {
      throw new NullPointerException(
          "Can't extract signature data from body, because body is null");
    }

    Object rawBody =
        HttpWebhookUtil.transformRawBodyToObject(
            payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers()));

    if (!isMapOfString(rawBody)) {
      return StringUtils.EMPTY;
    }
    Map<String, String> signatureData = (Map<String, String>) rawBody;
    List<String> sortedKeys = new ArrayList<>(signatureData.keySet());
    Collections.sort(sortedKeys);

    StringBuilder builder = new StringBuilder();

    for (String key : sortedKeys) {
      builder.append(key);
      String value = signatureData.get(key);
      builder.append(value == null ? "" : value);
    }
    return builder.toString();
  }

  private static boolean isMapOfString(Object o) {
    if (o instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getValue() instanceof String && entry.getKey() instanceof String) {
          return true;
        }
      }
      return false;
    }
    return false;
  }

  @Override
  public byte[] getBytesToSign(final WebhookProcessingPayload payload) {
    return (payload.requestURL() + extractSignatureData(payload)).getBytes();
  }
}
