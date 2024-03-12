package io.camunda.connector.api.error.retry;

import static io.camunda.connector.api.error.retry.ConnectorRetryException.DEFAULT_RETRY_ERROR_CODE;
import static io.camunda.connector.api.error.retry.ConnectorRetryException.DEFAULT_RETRY_POLICY;

/** Builder for creating a {@link ConnectorRetryException}. */
public class ConnectorRetryExceptionBuilder {
  private String message;

  private String errorCode = DEFAULT_RETRY_ERROR_CODE;

  private Throwable cause;

  private ConnectorRetryException.RetryPolicy retryPolicy = DEFAULT_RETRY_POLICY;

  public ConnectorRetryExceptionBuilder cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  public ConnectorRetryExceptionBuilder errorCode(String errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public ConnectorRetryExceptionBuilder message(String message) {
    this.message = message;
    return this;
  }

  public ConnectorRetryExceptionBuilder retryPolicy(
      ConnectorRetryException.RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
    return this;
  }

  /**
   * Builds a new {@link ConnectorRetryException}.
   *
   * @return the exception
   * @throws IllegalArgumentException if none of message, or cause is set
   */
  public ConnectorRetryException build() throws IllegalArgumentException {
    if (message == null && cause == null) {
      throw new IllegalArgumentException("At least one of message, or cause must be set.");
    }
    return new ConnectorRetryException(errorCode, message, cause, retryPolicy);
  }
}
