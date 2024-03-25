package io.camunda.connector.runtime.inbound.state;

import java.util.Map;

public record ProcessImportResult(
    Map<ProcessDefinitionIdentifier, ProcessDefinitionVersion> processDefinitionVersions
) {
  public record ProcessDefinitionIdentifier(String bpmnProcessId, String tenantId) {}

  public record ProcessDefinitionVersion(
      long processDefinitionKey,
      int version
  ) {}
}