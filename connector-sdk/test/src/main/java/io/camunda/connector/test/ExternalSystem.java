package io.camunda.connector.test;

import java.util.Arrays;

/**
 * Enumeration of external systems for system integration tests. The {@link #id} value is also
 * used as the expected environment variable name that must be present to enable tests
 * annotated with {@link SystemIntegrationTest}.
 */
public enum ExternalSystem {
  ServiceNow("ServiceNow"),
  SAP("SAP");

  public final String id;

  ExternalSystem(String id) {
    this.id = id;
  }
}

