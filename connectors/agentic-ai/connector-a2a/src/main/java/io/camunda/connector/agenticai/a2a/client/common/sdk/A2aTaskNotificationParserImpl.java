/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverter;
import java.nio.charset.StandardCharsets;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;

public class A2aTaskNotificationParserImpl implements A2aTaskNotificationParser {

  private final A2aSdkObjectConverter sdkObjectConverter;

  public A2aTaskNotificationParserImpl(A2aSdkObjectConverter sdkObjectConverter) {
    this.sdkObjectConverter = sdkObjectConverter;
  }

  @Override
  public A2aTask parse(byte[] payload) {
    final StreamingEventKind event;
    try {
      event =
          JsonUtil.fromJson(new String(payload, StandardCharsets.UTF_8), StreamingEventKind.class);
    } catch (JsonProcessingException | RuntimeException e) {
      throw new IllegalArgumentException(
          "Could not parse A2A push notification payload: " + e.getMessage(), e);
    }

    if (event instanceof Task task) {
      return sdkObjectConverter.convert(task);
    }

    throw new IllegalArgumentException(
        "Unsupported A2A push notification payload. Only task notifications are supported.");
  }
}
