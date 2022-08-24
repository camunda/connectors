package io.camunda.connector.inbound.importer;

import io.camunda.connector.inbound.connector.ConnectorProperties;
import io.camunda.connector.inbound.connector.ConnectorService;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Importer {

  private static final Logger LOG = LoggerFactory.getLogger(Importer.class);
  private static final String INDEX_NAME = "deployment";

  @Autowired
  DeploymentRecordRepository deploymentRecordRepository;

  @Autowired
  ImporterPositionRepository importerPositionRepository;

  @Autowired
  ConnectorService connectorService;

  @Scheduled(fixedDelay = 5000)
  public void scheduleImport() {
    LOG.debug("Starting import of deployment records");

    final ImporterPosition previousPosition = getPreviousPosition();
    LOG.debug("Found last imported position {}", previousPosition);

    final long lastImportedPosition = loadDeploymentRecords(previousPosition.position());
    if (lastImportedPosition > previousPosition.position()) {
      LOG.debug("Update last imported position to {}", lastImportedPosition);
      importerPositionRepository.save(
          new ImporterPosition(previousPosition.id(), lastImportedPosition));
    }

    LOG.debug("Finished import of deployment records");
  }

  private ImporterPosition getPreviousPosition() {
    return importerPositionRepository.findById(INDEX_NAME)
        .orElse(new ImporterPosition(INDEX_NAME, -1));
  }

  private long loadDeploymentRecords(final long lastPosition) {
    final Iterable<DeploymentRecord> iterable = getNextDeploymentRecords(lastPosition);
    final List<Long> positions = StreamSupport.stream(iterable.spliterator(), false)
        .map(this::loadDeploymentRecord)
        .toList();
    return positions.isEmpty() ? lastPosition : positions.get(positions.size() - 1);
  }

  private Iterable<DeploymentRecord> getNextDeploymentRecords(final long lastPosition) {
    try {
      return deploymentRecordRepository.findAllByPositionGreaterThan(
          lastPosition, Sort.by("position").ascending());
    } catch (NoSuchIndexException e) {
      LOG.debug("No Zeebe deployment record index found, skipping");
      return Collections.emptyList();
    }
  }

  private long loadDeploymentRecord(final DeploymentRecord deploymentRecord) {
    if (DeploymentIntent.CREATED.name().equals(deploymentRecord.intent())) {
      LOG.debug("Importing record with id {}, key {} and intent {}", deploymentRecord.id(),
          deploymentRecord.key(), deploymentRecord.intent());
      final DeploymentRecordValue value = deploymentRecord.value();
      final Map<String, byte[]> resources = value.resources().stream()
          .collect(Collectors.toMap(Resource::resourceName, Resource::resource));

      LOG.debug("Deployment record contains resources: {}", resources.keySet());

      value.processesMetadata()
          .stream().flatMap(
              processMetadata -> {
                final byte[] resource = resources.get(processMetadata.resourceName());
                final BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(
                    new ByteArrayInputStream(resource));
                return bpmnModelInstance.getDefinitions()
                    .getChildElementsByType(Process.class)
                    .stream().flatMap(
                        process -> process.getChildElementsByType(StartEvent.class).stream()
                    )
                    .map(startEvent -> startEvent.getSingleExtensionElement(ZeebeProperties.class))
                    .filter(Objects::nonNull)
                    .map(
                        zeebeProperties -> ConnectorProperties.from(processMetadata.bpmnProcessId(),
                            processMetadata.version(), zeebeProperties.getProperties()));
              }).forEach(connectorService::registerConnector);
    } else {
      LOG.debug("Ignoring record with id {}, key {} and intent {}", deploymentRecord.id(),
          deploymentRecord.key(), deploymentRecord.intent());
    }

    return deploymentRecord.position();
  }

}
