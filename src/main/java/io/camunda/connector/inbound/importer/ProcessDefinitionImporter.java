package io.camunda.connector.inbound.importer;

import io.camunda.connector.inbound.operate.OperateClientFactory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Component
public class ProcessDefinitionImporter {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionImporter.class);

  @Autowired
  private InboundConnectorRegistry registry;

  @Autowired
  private OperateClientFactory operateClientFactory;

  @Scheduled(fixedDelay = 5000)
  public void scheduleImport() throws OperateException {
    LOG.trace("Query process deployments...");
    // Lazy initialize the client - could be replaced by some Spring tricks later
    CamundaOperateClient camundaOperateClient = operateClientFactory.camundaOperateClient();

    // TODO: Think about pagination if we really have more process definitions
    SearchQuery processDefinitionQuery = new SearchQuery.Builder()
            .withSize(1000)
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
                    // Avoid issue with OpenJDK when collecting null values
                    // --> https://stackoverflow.com/questions/24630963/nullpointerexception-in-collectors-tomap-with-null-entry-values
                    // .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue)));
                    .collect(HashMap::new, (m, zeebeProperty)->m.put(zeebeProperty.getName(), zeebeProperty.getValue()), HashMap::putAll));

    if (InboundConnectorProperties.TYPE_WEBHOOK.equals(properties.getType())) {

      LOG.debug("Found inbound webhook connector: " + properties);
      registry.registerWebhookConnector(properties);

    } else {

      LOG.warn("Found other connector than webhook, which is not yet supported: " + properties);
      //registry.registerOtherInboundConnector(properties);

    }
  }

}
