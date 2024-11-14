package io.camunda.connector.api.inbound;

public sealed interface ActivationCheckResult {

  sealed interface Success extends ActivationCheckResult {
    ProcessElementContext activatedElement();

    record CanActivate(ProcessElementContext activatedElement) implements Success {}
  }

  sealed interface Failure extends ActivationCheckResult {

    record NoMatchingElement(boolean discardUnmatchedEvents) implements Failure {}

    record TooManyMatchingElements() implements Failure {}
  }
}
