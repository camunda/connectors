package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorDataMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorDataMapper.class);

  public Map<String, String> webhookMapper(ActiveExecutableResponse response) {
    Map<String, String> data = Map.of();
    var executableClass = response.executableClass();

    if (executableClass != null
        && WebhookConnectorExecutable.class.isAssignableFrom(executableClass)) {
      try {
        var properties = response.elements().getFirst().connectorLevelProperties();
        var contextPath = properties.get("inbound.context");
        data = Map.of("path", contextPath);
      } catch (Exception e) {
        LOG.error("ERROR: webhook connector doesn't have context path property", e);
      }
    }
    return data;
  }

  public Map<String, String> allPropertiesMapper(ActiveExecutableResponse response) {
    return response.elements().getFirst().connectorLevelProperties();
  }
}
