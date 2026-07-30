/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.generator.java.annotation.DataExample;
import java.util.Map;

public class WebhookOutputExample {

  public record ResultExpressionContext(MappedHttpRequest request) {}

  @DataExample(feel = "= { orderId: request.body.orderId }")
  public static ResultExpressionContext example() {
    return new ResultExpressionContext(
        new MappedHttpRequest(
            Map.of("orderId", "123", "status", "created"),
            Map.of("Content-Type", "application/json"),
            Map.of()));
  }
}
