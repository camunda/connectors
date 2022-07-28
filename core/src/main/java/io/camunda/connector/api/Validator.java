package io.camunda.connector.api;

import java.util.ArrayList;
import java.util.List;

public class Validator {
  protected static final String PROPERTIES_MISSING_MSG =
      "Evaluation failed with following errors: %s";
  protected static final String PROPERTY_REQUIRED_MSG = "Property required: ";

  private final List<String> errorMessages = new ArrayList<>();

  public void addErrorMessage(final String errorMessage) {
    if (errorMessage != null) {
      errorMessages.add(errorMessage);
    }
  }

  public void require(final Object property, final String propertyName) {
    if (property == null) {
      addErrorMessage(PROPERTY_REQUIRED_MSG + propertyName);
    }
  }

  public void evaluate() {
    if (!errorMessages.isEmpty()) {
      throw new IllegalArgumentException(getEvaluationResultMessage());
    }
  }

  private String getEvaluationResultMessage() {
    return String.format(PROPERTIES_MISSING_MSG, String.join(", ", errorMessages));
  }
}
