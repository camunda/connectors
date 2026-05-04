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
package io.camunda.connector.runtime.outbound;

import static org.mockito.Mockito.when;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.common.AbstractConnectorFactory.ConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.instances.service.DefaultInstanceForwardingService;
import io.camunda.connector.runtime.instances.service.InstanceForwardingService;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

abstract class BaseOutboundMultiInstancesTest {

  static final String TYPE_1 = "io.camunda:http-json:1";
  static final String TYPE_2 = "io.camunda:slack:1";
  static final String TYPE_ONLY_IN_INSTANCE_2 = "io.camunda:only-in-instance-2:1";
  static final String TYPE_DISABLED_IN_INSTANCE_1 = "io.camunda:disabled-in-instance-1:1";

  final int port1 = 18082;
  final int port2 = 18083;

  final OutboundConnectorFactory connectorFactory1 = Mockito.mock(OutboundConnectorFactory.class);
  final OutboundConnectorFactory connectorFactory2 = Mockito.mock(OutboundConnectorFactory.class);

  final InstanceForwardingHttpClient instanceForwardingHttpClient =
      new InstanceForwardingHttpClient(
          HttpClient.newHttpClient(),
          (path) -> List.of("http://localhost:" + port1 + path, "http://localhost:" + port2 + path),
          ConnectorsObjectMapperSupplier.getCopy());

  final TestRestTemplate restTemplate =
      new TestRestTemplate(
          new RestTemplateBuilder()
              .messageConverters(
                  new StringHttpMessageConverter(),
                  new JacksonJsonHttpMessageConverter(JsonMapper.builderWithJackson2Defaults())));

  ConfigurableApplicationContext context1;
  ConfigurableApplicationContext context2;

  @AfterEach
  void tearDown() {
    if (context1 != null) context1.close();
    if (context2 != null) context2.close();
  }

  @BeforeEach
  void init() {
    context1 =
        new SpringApplicationBuilder(TestConnectorRuntimeApplication.class)
            .properties(
                "server.port=" + port1,
                "spring.application.name=instance1",
                "camunda.connector.hostname=instance1",
                "camunda.connector.headless.serviceurl=http://whatever:8080")
            .initializers(
                ctx -> {
                  ((GenericApplicationContext) ctx)
                      .registerBean(OutboundConnectorFactory.class, () -> connectorFactory1);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingHttpClient.class, () -> instanceForwardingHttpClient);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingService.class,
                          () ->
                              new DefaultInstanceForwardingService(
                                  instanceForwardingHttpClient, "instance1"));
                })
            .run();

    context2 =
        new SpringApplicationBuilder(TestConnectorRuntimeApplication.class)
            .properties(
                "server.port=" + port2,
                "spring.application.name=instance2",
                "camunda.connector.hostname=instance2",
                "camunda.connector.headless.serviceurl=http://whatever:8080")
            .initializers(
                ctx -> {
                  ((GenericApplicationContext) ctx)
                      .registerBean(OutboundConnectorFactory.class, () -> connectorFactory2);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingHttpClient.class, () -> instanceForwardingHttpClient);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingService.class,
                          () ->
                              new DefaultInstanceForwardingService(
                                  instanceForwardingHttpClient, "instance2"));
                })
            .run();

    // instance1: TYPE_1 (enabled), TYPE_2 (enabled), TYPE_DISABLED_IN_INSTANCE_1 (disabled)
    when(connectorFactory1.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"method", "url"}, TYPE_1, () -> null, 30000L),
                    true),
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "Slack", new String[] {"channel"}, TYPE_2, () -> null, null),
                    true),
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "Disabled In Instance 1",
                        new String[] {},
                        TYPE_DISABLED_IN_INSTANCE_1,
                        () -> null,
                        null),
                    false)));

    // instance2: TYPE_1 (enabled), TYPE_2 (enabled), TYPE_ONLY_IN_INSTANCE_2 (enabled)
    when(connectorFactory2.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"method", "url"}, TYPE_1, () -> null, 30000L),
                    true),
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "Slack", new String[] {"channel"}, TYPE_2, () -> null, null),
                    true),
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "Only In Instance 2",
                        new String[] {},
                        TYPE_ONLY_IN_INSTANCE_2,
                        () -> null,
                        null),
                    true)));
  }
}
