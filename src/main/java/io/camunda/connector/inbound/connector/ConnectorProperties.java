package io.camunda.connector.inbound.connector;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "connector-inbound-properties")
@Setting(replicas = 0)
public record ConnectorProperties(@Id String context, String bpmnProcessId, int version, String type,
                                  String secretExtractor, String activationCondition,
                                  String variableMapping) {

  public static ConnectorProperties from(String bpmnProcessId, int version,
      Collection<ZeebeProperty> properties) {
    final Map<String, String> propertiesMap = properties.stream()
        .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue));

    return new ConnectorProperties(
        readInboundProperty(propertiesMap, "context"),
        bpmnProcessId,
        version,
        readInboundProperty(propertiesMap, "type"),
        readInboundProperty(propertiesMap, "secretExtractor"),
        readInboundProperty(propertiesMap, "activationCondition"),
        readInboundProperty(propertiesMap, "variableMapping")
    );
  }

  private static String readInboundProperty(Map<String, String> properties, String name) {
    return properties.get("inbound." + name);
  }

}
