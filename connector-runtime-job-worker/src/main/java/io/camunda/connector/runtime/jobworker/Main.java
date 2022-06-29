package io.camunda.connector.runtime.jobworker;

import io.camunda.connector.sdk.ConnectorFunction;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {

    final String defaultAddress = "localhost:26500";
    final String envVarAddress = System.getenv("ZEEBE_ADDRESS");

    final ZeebeClientBuilder clientBuilder;

    if (envVarAddress != null) {
      /**
       * Connect to Camunda Cloud Cluster, assumes that credentials are set in environment
       * variables. See JavaDoc on class level for details
       */
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(envVarAddress);
    } else {
      /** Connect to local deployment; assumes that authentication is disabled */
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(defaultAddress).usePlaintext();
    }

    var connectors = ConnectorConfig.parse();

    try (ZeebeClient client = clientBuilder.build()) {

      for (var connector : connectors) {

        LOGGER.info(
            "Registering connector {} on topic {}", connector.getFunction(), connector.getType());

        var connectorFunction = loadConnectorFunction(connector.getFunction());

        client
            .newWorker()
            .jobType(connector.getType())
            .handler(new ConnectorJobHandler(connectorFunction))
            .timeout(Duration.ofSeconds(10))
            .name(connector.getName())
            .open();
      }
    }
  }

  private static ConnectorFunction loadConnectorFunction(String clsName) {

    try {
      var cls = (Class<ConnectorFunction>) Class.forName(clsName);

      return cls.getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException
        | InvocationTargetException
        | InstantiationException
        | IllegalAccessException
        | ClassCastException
        | NoSuchMethodException e) {
      throw loadFailed("Failed to load " + clsName, e);
    }
  }

  private static RuntimeException loadFailed(String s, Exception e) {
    throw new IllegalStateException(s, e);
  }
}
