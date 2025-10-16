package io.camunda.connector.rabbitmq.outbound;

public class RabbitMqDeleteResult {

  private String status;
  private String errorMessage;

  public RabbitMqDeleteResult() {}

  public RabbitMqDeleteResult(final String status, final String errorMessage) {
    this.status = status;
    this.errorMessage = errorMessage;
  }

  public static RabbitMqDeleteResult success() {
    return new RabbitMqDeleteResult("success", null);
  }

  public static RabbitMqDeleteResult failure(final String message) {
    return new RabbitMqDeleteResult("failure", message);
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @Override
  public String toString() {
    return "RabbitMqDeleteResult{"
        + "status='"
        + status
        + '\''
        + ", errorMessage='"
        + errorMessage
        + '\''
        + '}';
  }
}
