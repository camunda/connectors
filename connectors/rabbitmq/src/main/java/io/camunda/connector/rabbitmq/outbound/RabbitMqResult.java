/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

public class RabbitMqResult {

  private String statusResult;

  public RabbitMqResult(final String statusResult) {
    this.statusResult = statusResult;
  }

  public static RabbitMqResult success() {
    return new RabbitMqResult("success");
  }

  public String getStatusResult() {
    return statusResult;
  }

  public void setStatusResult(final String statusResult) {
    this.statusResult = statusResult;
  }

  @Override
  public String toString() {
    return "RabbitMqResult{" + "statusResult='" + statusResult + "'" + "}";
  }
}
