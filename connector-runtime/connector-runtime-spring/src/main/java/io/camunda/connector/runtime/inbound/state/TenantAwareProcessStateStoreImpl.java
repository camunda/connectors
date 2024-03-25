package io.camunda.connector.runtime.inbound.state;

import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TenantAwareProcessStateStoreImpl implements ProcessStateStore {

  private final ConcurrentHashMap<String, ProcessStateStoreImpl> processStateStores = new ConcurrentHashMap<>();

  private final ProcessDefinitionInspector processDefinitionInspector;
  private final InboundExecutableRegistry executableRegistry;

  public TenantAwareProcessStateStoreImpl(
      ProcessDefinitionInspector processDefinitionInspector,
      InboundExecutableRegistry executableRegistry
  ) {
    this.processDefinitionInspector = processDefinitionInspector;
    this.executableRegistry = executableRegistry;
  }

  @Override
  public void update(ProcessImportResult processDefinitions) {
    var groupedByTenant = processDefinitions.processDefinitionVersions().entrySet().stream()
        .collect(Collectors.groupingBy(entry -> entry.getKey().tenantId()));

    groupedByTenant.forEach((tenantId, definitions) -> {
      var store = processStateStores.computeIfAbsent(tenantId,
          key -> new ProcessStateStoreImpl(processDefinitionInspector, executableRegistry));

      var tenantProcessDefinitions = new ProcessImportResult(
          definitions.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
      store.update(tenantProcessDefinitions);
    });
  }
}
