package io.camunda.connector.inbound.connector;

public class FeelEngineWrapperException extends RuntimeException {

  public FeelEngineWrapperException(
      final String reason, final String expression, final Object context) {
    super(
        String.format(
            "Failed to evaluate expression '%s' and context '%s', because %s",
            expression, context, reason));
  }
}
