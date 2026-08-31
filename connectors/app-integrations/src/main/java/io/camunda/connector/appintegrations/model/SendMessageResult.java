/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;

/**
 * Every destination the message reached and every one it did not. Both lists are reported, so a
 * single delivery is a one-element {@code deliveries} that a modeler reads as {@code deliveries[1]}
 * (FEEL is 1-indexed), and a partial failure is visible rather than silent.
 */
@JsonInclude(Include.NON_NULL)
public record SendMessageResult(List<Delivery> deliveries, List<Failure> failures) {

  /**
   * What a later send passes back to reply: for Slack {@code conversation} is the channel target
   * and {@code messageId} the thread anchor; for Teams {@code conversation} is the conversation
   * target.
   */
  public record Delivery(String platform, String conversation, String messageId) {}

  public record Failure(String platform, String conversation, String reason) {}
}
