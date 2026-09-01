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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * No {@code camunda.connector.secret-resolver.secret-filter.mode} property is set here, so this
 * exercises the {@code @Value} default on {@code springInboundConnectorContextFactory} itself —
 * unlike a direct call to that bean method, which bypasses Spring's property resolution entirely.
 */
@SpringBootTest(classes = TestConnectorRuntimeApplication.class)
class InboundSecretFilterDefaultModeWiringTest {

  @Autowired private InboundConnectorContextFactory contextFactory;

  static class TestExecutable implements InboundConnectorExecutable<InboundConnectorContext> {
    @Override
    public void activate(InboundConnectorContext context) {}

    @Override
    public void deactivate() {}
  }

  @Test
  void defaultsToFilteringSecrets() {
    var properties = Map.of("inbound.type", "io.camunda:connector:1");
    var element =
        new InboundConnectorElement(
            properties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElementWithRuntimeData(
                "process",
                null,
                null,
                0,
                0,
                "id",
                null,
                null,
                "<default>",
                "default",
                new ElementTemplateDetails("Test", "1", "icon"),
                properties));
    var details =
        (ValidInboundConnectorDetails)
            InboundConnectorDetails.of(element.deduplicationId(List.of()), List.of(element));

    var context =
        (InboundConnectorContextImpl)
            contextFactory.createContext(details, e -> {}, TestExecutable.class, entry -> {});
    var result =
        context
            .getSecretHandler()
            .replaceSecrets("secrets.UNDECLARED", new SecretContext("t", "p"));

    assertThat(result).isEqualTo("secrets.UNDECLARED");
  }
}
