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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.OperateClientAdapter;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InboundConnectorRuntimeConfigurationTest {

  private final InboundConnectorRuntimeConfiguration configuration =
      new InboundConnectorRuntimeConfiguration();

  /**
   * Pins the {@code SecretFilterMode} -> boolean hop on the connector-level path: nothing else
   * asserts that the mode reaches the context, and because {@code LAX} and {@code STRICT} behave
   * identically inbound this is the only place their equivalence — and {@code DISABLED}'s
   * difference from both — is checked. Fails if the argument is dropped or the boolean inverted.
   */
  @Test
  void springInboundConnectorContextFactory_filtersSecretsUnlessModeIsDisabled() {
    assertEquals("secrets.UNDECLARED", resolveUndeclared(SecretFilterMode.STRICT));
    assertEquals("secrets.UNDECLARED", resolveUndeclared(SecretFilterMode.LAX));
    assertEquals("resolved", resolveUndeclared(SecretFilterMode.DISABLED));
  }

  /**
   * The second inbound resolution path. Every successful {@code CorrelationResult} carries a {@code
   * ProcessElementContext} as its {@code activatedElement} — built at three sites in {@code
   * InboundCorrelationHandler} — and its public {@code getProperties()}/{@code bindProperties()}
   * resolve secrets through their own {@code SecretHandler}. Filtering only {@code
   * springInboundConnectorContextFactory} would leave this one allow-all. It is the analogue of the
   * {@code BindableProcessElement} path #8538 filters upstream.
   *
   * <p>Exercised at the factory rather than through {@code correlate()}, which would need the whole
   * Zeebe publish/create round trip mocked to reach a {@code Success}; what needs pinning here is
   * that the mode reaches the context the correlation handler hands out, and that is this factory.
   *
   * <p>The probe has to be the chained case: this context resolves the element's own property text,
   * so any name written there is declared by definition and the allow-list is a superset of it.
   *
   * <p>DISABLED used to resolve that to {@code leaked-value}, because the bare pass ran over the
   * brace pass's output and the filter was the only thing standing in the way. The single scan
   * removes the second pass, so the chain is closed at its source and all three modes refuse the
   * name. The probe no longer discriminates between modes, and there is no longer a way to
   * construct an undeclared name on this path: what the filter here guards against is a resolution
   * step that no longer exists. The filter itself stays, and {@link
   * #springInboundConnectorContextFactory_filtersSecretsUnlessModeIsDisabled} still exercises it on
   * the path where a caller supplies the text.
   */
  @Test
  void processElementContextFactory_refusesAChainedNameOnTheActivatedElement() {
    assertEquals("secrets.CHAINED", resolveChainedViaElementContext(SecretFilterMode.STRICT));
    assertEquals("secrets.CHAINED", resolveChainedViaElementContext(SecretFilterMode.LAX));
    assertEquals("secrets.CHAINED", resolveChainedViaElementContext(SecretFilterMode.DISABLED));
  }

  private String resolveUndeclared(SecretFilterMode mode) {
    var factory =
        configuration.springInboundConnectorContextFactory(
            new ObjectMapper(),
            mock(InboundCorrelationHandler.class),
            new SecretProviderAggregator(List.of(alwaysResolvingProvider())),
            mock(ValidationProvider.class),
            mock(OperateClientAdapter.class),
            mode);
    var context =
        (InboundConnectorContextImpl)
            factory.createContext(
                detailsDeclaringOnly("token", "secrets.DECLARED"),
                e -> {},
                TestExecutable.class,
                EvictingQueue.create(10));
    return context.getSecretHandler().replaceSecrets("secrets.UNDECLARED");
  }

  private String resolveChainedViaElementContext(SecretFilterMode mode) {
    var elementFactory =
        configuration.processElementContextFactory(
            new ObjectMapper(),
            mock(ValidationProvider.class),
            new SecretProviderAggregator(List.of(chainingProvider())),
            mode);
    var elementContext = elementFactory.createContext(chainRootElement());
    return (String) elementContext.getProperties().get("token");
  }

  private static ValidInboundConnectorDetails detailsDeclaringOnly(String key, String value) {
    var element = element(Map.of("inbound.type", "io.camunda:connector:1", key, value));
    return (ValidInboundConnectorDetails)
        InboundConnectorDetails.of(element.deduplicationId(List.of()), List.of(element));
  }

  private static InboundConnectorElement chainRootElement() {
    return element(
        Map.of("inbound.type", "io.camunda:connector:1", "token", "{{secrets.CHAIN_ROOT}}"));
  }

  private static InboundConnectorElement element(Map<String, String> properties) {
    return new InboundConnectorElement(
        properties,
        new StandaloneMessageCorrelationPoint("", "", null, null),
        new ProcessElement("process", 0, 0, "id", "<default>"));
  }

  private static SecretProvider alwaysResolvingProvider() {
    return name -> "resolved";
  }

  private static SecretProvider chainingProvider() {
    return name ->
        switch (name.trim()) {
          case "CHAIN_ROOT" -> "secrets.CHAINED";
          case "CHAINED" -> "leaked-value";
          default -> null;
        };
  }

  static class TestExecutable implements InboundConnectorExecutable<InboundConnectorContext> {
    @Override
    public void activate(InboundConnectorContext context) {}

    @Override
    public void deactivate() {}
  }
}
