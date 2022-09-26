/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.runtime.jobworker;

import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.jobworker.api.outbound.ConnectorJobHandler;
import io.camunda.connector.runtime.jobworker.impl.outbound.OutboundConnectorConfig;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  /**
   * Start a Zeebe client with handlers for all defined connector functions
   *
   * @param args no arguments are evaluated for this class
   */
  public static void main(String[] args) {

    final String defaultAddress = "localhost:26500";
    final String envVarAddress = System.getenv("ZEEBE_ADDRESS");
    final String envVarInsecure = System.getenv("ZEEBE_INSECURE");

    final ZeebeClientBuilder clientBuilder;

    if (envVarAddress != null) {
      /*
       * Connect to Camunda Cloud Cluster, assumes that credentials are set in environment
       * variables. See JavaDoc on class level for details
       */
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(envVarAddress);
      if (Boolean.parseBoolean(envVarInsecure)) {
        clientBuilder.usePlaintext();
      }
    } else {
      /* Connect to local deployment; assumes that authentication is disabled */
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(defaultAddress).usePlaintext();
    }

    var connectors = OutboundConnectorConfig.parse();

    if (connectors.isEmpty()) {
      throw new IllegalStateException("No connectors configured");
    }

    try (ZeebeClient client = clientBuilder.build()) {

      final var workers =
          connectors.stream()
              .map(
                  connector -> {
                    LOGGER.info(
                        "Registering connector function {} as {} on job type {} with variables {}",
                        connector.getFunction(),
                        connector.getName(),
                        connector.getType(),
                        connector.getVariables());

                    var connectorFunction = loadConnectorFunction(connector.getFunction());

                    return client
                        .newWorker()
                        .jobType(connector.getType())
                        .handler(new ConnectorJobHandler(connectorFunction))
                        .timeout(Duration.ofSeconds(10))
                        .name(connector.getName())
                        .fetchVariables(connector.getVariables())
                        .open();
                  })
              .collect(Collectors.toList());

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    LOGGER.info("Shutting down workers...");
                    workers.forEach(
                        worker -> {
                          try {
                            worker.close();
                          } catch (Exception e) {
                            // ignore
                          }
                        });
                  }));

      waitForever();
    }
  }

  @SuppressWarnings("unchecked")
  private static OutboundConnectorFunction loadConnectorFunction(String clsName) {

    try {
      var cls = (Class<OutboundConnectorFunction>) Class.forName(clsName);

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

  private static void waitForever() {
    try {
      while (true) {
        Thread.sleep(3000);
      }
    } catch (Exception e) {
      // ignore
    }
  }
}
