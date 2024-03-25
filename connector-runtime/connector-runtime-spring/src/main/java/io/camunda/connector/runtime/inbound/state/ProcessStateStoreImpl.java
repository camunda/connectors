package io.camunda.connector.runtime.inbound.state;

import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElementImpl;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionVersion;
import io.camunda.operate.exception.OperateException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessStateStoreImpl implements ProcessStateStore {

  private final static Logger LOG = LoggerFactory.getLogger(ProcessStateStoreImpl.class);

  private final ConcurrentHashMap<String, ProcessState> processStates = new ConcurrentHashMap<>();

  private final ProcessDefinitionInspector processDefinitionInspector;
  private final InboundExecutableRegistry executableRegistry;

  private record ProcessState(
      int version,
      long processDefinitionKey,
      Map<String, UUID> executablesByDeduplicationId
  ) {}

  public ProcessStateStoreImpl(
      ProcessDefinitionInspector processDefinitionInspector,
      InboundExecutableRegistry executableRegistry
  ) {
    this.processDefinitionInspector = processDefinitionInspector;
    this.executableRegistry = executableRegistry;
  }

  @Override
  public void update(ProcessImportResult processDefinitions) {
    var entries = processDefinitions.processDefinitionVersions().entrySet();

    var newlyDeployed = entries.stream()
        .filter(entry -> !processStates.containsKey(entry.getKey().bpmnProcessId()))
        .toList();

    var replacedWithDifferentVersion = entries.stream()
        .filter(entry -> {
          var state = processStates.get(entry.getKey().bpmnProcessId());
          return state != null && state.version() != entry.getValue().version();
        })
        .toList();

    var deletedProcessIds = processStates.keySet().stream()
        .filter(processState -> processDefinitions.processDefinitionVersions().keySet().stream()
            .noneMatch(
                key -> key.bpmnProcessId().equals(processState)
            ))
        .toList();

    logResult(newlyDeployed, replacedWithDifferentVersion, deletedProcessIds);

    newlyDeployed.forEach(this::newlyDeployed);
    replacedWithDifferentVersion.forEach(this::replacedWithDifferentVersion);
    deletedProcessIds.forEach(this::deleted);
  }

  private void newlyDeployed(
      Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion> entry) {
    try {
      processStates.compute(entry.getKey().bpmnProcessId(), (key, state) -> {
        var connectorDefinitions = getConnectors(entry);
        var executables = connectorDefinitions.stream()
            .collect(Collectors.toMap(
                InboundConnectorDefinitionImpl::deduplicationId,
                this::activateExecutable
            ));

        return new ProcessState(
            entry.getValue().version(),
            entry.getValue().processDefinitionKey(),
            executables
        );
      });
    } catch (Throwable e) {
      LOG.error("Failed to register process {}", entry.getKey().bpmnProcessId(), e);
      // ignore and continue with the next process
    }
  }

  private void replacedWithDifferentVersion(
      Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion> entry) {
    try {
      processStates.computeIfPresent(entry.getKey().bpmnProcessId(), (key, state) -> {
        var connectorDefinitions = getConnectors(entry);
        state.executablesByDeduplicationId().values().forEach(this::deactivateExecutable);
        var newExecutables = connectorDefinitions.stream()
            .collect(Collectors.toMap(
                InboundConnectorDefinitionImpl::deduplicationId,
                this::activateExecutable
            ));
        return new ProcessState(
            entry.getValue().version(),
            entry.getValue().processDefinitionKey(),
            newExecutables
        );
      });
    } catch (Throwable e) {
      LOG.error("Failed to update process {}", entry.getKey().bpmnProcessId(), e);
      // ignore and continue with the next process
    }
  }

  private void deleted(String processId) {
    try {
      processStates.computeIfPresent(processId, (key1, state) -> {
        state.executablesByDeduplicationId.values().forEach(this::deactivateExecutable);
        return null;
      });
    } catch (Throwable e) {
      LOG.error("Failed to deregister process {}", processId, e);
      // ignore and continue with the next process
    }
  }

  private List<InboundConnectorDefinitionImpl> getConnectors(
      Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion> entry) {
    try {
      var elements = processDefinitionInspector.findInboundConnectors(entry);
      if (elements.isEmpty()) {
        LOG.debug("No inbound connectors found for process {}", entry.getKey().bpmnProcessId());
      }
      var groupedByDeduplicationId = elements.stream().collect(
          Collectors.groupingBy(InboundConnectorElementImpl::deduplicationId));
      return groupedByDeduplicationId.values().stream()
          .map(InboundConnectorDefinitionImpl::new)
          .toList();
    } catch (OperateException e) {
      throw new RuntimeException(e);
    }
  }

  private UUID activateExecutable(InboundConnectorDefinitionImpl definition) {
    var id = UUID.randomUUID();
    var event = new InboundExecutableEvent.Activated(id, definition);
    executableRegistry.handleEvent(event);
    return id;
  }

  private void deactivateExecutable(UUID id) {
    var event = new InboundExecutableEvent.Deactivated(id);
    executableRegistry.handleEvent(event);
  }

  private void logResult(
      List<Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion>> brandNew,
      List<Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion>> upgraded,
      List<String> deleted) {

    if (brandNew.isEmpty() && upgraded.isEmpty() && deleted.isEmpty()) {
      LOG.debug("No changes in process elements");
      return;
    }
    LOG.info("Detected changes in process elements");
    LOG.info(". {} newly deployed", brandNew.size());
    for (var pd : brandNew) {
      LOG.info(
          ". Process: {}, version: {} for tenant: {}",
          pd.getKey().bpmnProcessId(),
          pd.getValue().version(),
          pd.getKey().tenantId());
    }
    LOG.info(". {} replaced with new version", upgraded.size());
    for (var pd : upgraded) {
      var oldVersion = processStates.get(pd.getKey().bpmnProcessId()).version();
      LOG.info(
          ". Process: {}, version {} - replaced with version {} for tenant: {}",
          pd.getKey().bpmnProcessId(),
          oldVersion,
          pd.getValue().version(),
          pd.getKey().tenantId());
    }
    LOG.info(". {} deleted", deleted.size());
    for (String key : deleted) {
      LOG.info(". . Process {}", key);
    }
  }
}
