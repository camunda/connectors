/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

public class RabbitMqDeleteResult {
  private String statusResult;
  private String message;

  public String getStatusResult() {
    return statusResult;
  }

  public void setStatusResult(String statusResult) {
    this.statusResult = statusResult;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public RabbitMqDeleteResult(final String statusResult, final String message) {
    this.statusResult = statusResult;
    this.message = message;
  }

  public static RabbitMqDeleteResult success() {
    return new RabbitMqDeleteResult("SUCCESS", "Queue operation completed successfully");
  }

  public static RabbitMqDeleteResult failure(String errorMessage) {
    return new RabbitMqDeleteResult("FAILURE", errorMessage);
  }
}
