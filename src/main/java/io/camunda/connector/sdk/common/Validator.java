package io.camunda.connector.sdk.common;

import java.util.ArrayList;
import java.util.List;

public class Validator {

  private final List<String> errors = new ArrayList<>();

  public void require(final Object object, final String propertyName) {
    if (object == null) {
      errors.add(propertyName);
    }
  }

  public void validate() {
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(getErrorMessage());
    }
  }

  private String getErrorMessage() {
    if (errors.size() == 1) {
      return String.format("Property '%s' is missing", errors.get(0));
    } else {
      return "The following properties are missing: " + String.join(", ", errors);
    }
  }
}
