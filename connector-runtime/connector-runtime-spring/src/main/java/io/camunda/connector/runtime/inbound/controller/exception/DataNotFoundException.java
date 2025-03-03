package io.camunda.connector.runtime.inbound.controller.exception;

public class DataNotFoundException extends RuntimeException {

  public DataNotFoundException(Class<?> dataClass, String dataId) {
    super(
        String.format(
            "Data of type '%s' with id '%s' not found", dataClass.getSimpleName(), dataId));
  }
}
