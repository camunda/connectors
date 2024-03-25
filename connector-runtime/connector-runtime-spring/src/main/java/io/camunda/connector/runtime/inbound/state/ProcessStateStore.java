package io.camunda.connector.runtime.inbound.state;

public interface ProcessStateStore {

  /**
   * Update the process state based on the latest versions of the process definitions.
   * Implementations must be idempotent.
   */
  void update(ProcessImportResult processDefinitions);
}
