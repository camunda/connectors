package io.camunda.connector.inbound.connector;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConnectorService {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorService.class);

  @Autowired private ConnectorPropertiesRepository respository;

  public void registerConnector(ConnectorProperties connectorProperties) {

    // TODO(nikku): validate that connector is properly configured

    if (connectorProperties.context() != null) {
      LOG.info("Registering new connector {}", connectorProperties);
      respository.save(connectorProperties);
    }
  }

  public Optional<ConnectorProperties> get(final String context) {
    return respository.findById(context);
  }
}
