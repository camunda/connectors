package io.camunda.connector.inbound.importer;

import io.camunda.connector.inbound.registry.InboundConnectorProperties;
import io.camunda.connector.inbound.registry.InboundConnectorRegistry;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.ProcessDefinition;
import io.camunda.operate.exception.OperateException;
import io.camunda.operate.search.SearchQuery;
import io.camunda.operate.search.Sort;
import io.camunda.operate.search.SortOrder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OperateImporter {

  private static final Logger LOG = LoggerFactory.getLogger(OperateImporter.class);

  @Autowired
  private InboundConnectorRegistry registry;

  @Autowired
  private CamundaOperateClient camundaOperateClient;

  @Scheduled(fixedDelay = 5000)
  public void scheduleImport() throws OperateException {
    LOG.trace("Query process deployments...");

    SearchQuery processDefinitionQuery = new SearchQuery.Builder()
            .withSort(new Sort("version", SortOrder.ASC))
            .build();

    List<ProcessDefinition> processDefinitions = camundaOperateClient
            .searchProcessDefinitions(processDefinitionQuery);
    LOG.trace("... returned " + processDefinitions.size() + " process definitions.");

    ArrayList<InboundConnectorProperties> connectorProperties = new ArrayList<>();
    for (ProcessDefinition processDefinition: processDefinitions) {

      if (!registry.processDefinitionChecked(processDefinition.getKey())) {
        LOG.debug("Check " + processDefinition + " for connectors.");

        String processDefinitionXml = camundaOperateClient.getProcessDefinitionXml(processDefinition.getKey());
        processBpmnXml(processDefinitionXml, processDefinition);

        registry.markProcessDefinitionChecked(processDefinition.getKey());
      }

    }
  }



  private void processBpmnXml(String resource, ProcessDefinition processDefinition) {
    final BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(
        new ByteArrayInputStream(resource.getBytes()));
    bpmnModelInstance.getDefinitions()
            .getChildElementsByType(Process.class)
            .stream().flatMap(
                    process -> process.getChildElementsByType(StartEvent.class).stream()
            )
            .map(startEvent -> startEvent.getSingleExtensionElement(ZeebeProperties.class))
            .filter(Objects::nonNull)
            .forEach(zeebeProperties -> processZeebeProperties(processDefinition, zeebeProperties));
    // TODO: Also process intermediate catching message events and Receive Tasks
  }

  private void processZeebeProperties(ProcessDefinition processDefinition, ZeebeProperties zeebeProperties) {
    InboundConnectorProperties properties = new InboundConnectorProperties(
            processDefinition.getBpmnProcessId(),
            processDefinition.getVersion().intValue(),
            processDefinition.getKey(),
            zeebeProperties.getProperties().stream()
                    .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue)));

    if (InboundConnectorProperties.TYPE_WEBHOOK.equals(properties.getType())) {

      LOG.debug("Found inbound webhook connector: " + properties);
      registry.registerWebhookConnector(properties);

    } else {

      LOG.warn("Found other connector than webhook, which is not yet supported: " + properties);
      //registry.registerOtherInboundConnector(properties);

    }
  }

}
