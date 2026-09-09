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
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.secret.SecretContext;
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
import io.camunda.document.factory.DocumentFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InboundConnectorRuntimeConfigurationTest {

  private final InboundConnectorRuntimeConfiguration configuration =
      new InboundConnectorRuntimeConfiguration();

  /**
   * Pins the {@code SecretFilterMode} -> boolean hop: nothing else asserts that the mode reaches
   * the context, and because {@code LAX} and {@code STRICT} behave identically on the inbound path
   * this is the only place their equivalence — and {@code DISABLED}'s difference from both — is
   * checked. Fails if the argument is dropped or the boolean inverted.
   *
   * <p>This calls the bean method directly and so bypasses Spring's {@code @Value} resolution
   * entirely; {@code InboundSecretFilterDefaultModeWiringTest} covers the default string itself.
   */
  @Test
  void springInboundConnectorContextFactory_filtersSecretsUnlessModeIsDisabled() {
    assertEquals(
        "secrets.UNDECLARED",
        resolveUndeclared(contextForSecretFilterMode(SecretFilterMode.STRICT)));
    assertEquals(
        "secrets.UNDECLARED", resolveUndeclared(contextForSecretFilterMode(SecretFilterMode.LAX)));
    assertEquals(
        "resolved", resolveUndeclared(contextForSecretFilterMode(SecretFilterMode.DISABLED)));
  }

  private InboundConnectorContextImpl contextForSecretFilterMode(SecretFilterMode mode) {
    var factory =
        configuration.springInboundConnectorContextFactory(
            new ObjectMapper(),
            mock(InboundCorrelationHandler.class),
            new SecretProviderAggregator(List.of(alwaysResolvingProvider())),
            mock(ValidationProvider.class),
            mock(OperateClientAdapter.class),
            mock(DocumentFactory.class),
            mode);
    return (InboundConnectorContextImpl)
        factory.createContext(
            detailsDeclaringNoSecret(), e -> {}, TestExecutable.class, EvictingQueue.create(10));
  }

  private static ValidInboundConnectorDetails detailsDeclaringNoSecret() {
    var properties = Map.of("inbound.type", "io.camunda:connector:1");
    var element =
        new InboundConnectorElement(
            properties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElement("process", 0, 0, "id", "<default>"));
    return (ValidInboundConnectorDetails)
        InboundConnectorDetails.of(element.deduplicationId(List.of()), List.of(element));
  }

  private static String resolveUndeclared(InboundConnectorContextImpl context) {
    var probe = new ObjectMapper().createObjectNode().put("value", "secrets.UNDECLARED");
    return context
        .getSecretHandler()
        .replaceSecrets(probe, new SecretContext("t"))
        .get("value")
        .asText();
  }

  private static SecretProvider alwaysResolvingProvider() {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return "resolved";
      }
    };
  }

  static class TestExecutable implements InboundConnectorExecutable<InboundConnectorContext> {
    @Override
    public void activate(InboundConnectorContext context) {}

    @Override
    public void deactivate() {}
  }

  /**
   * The second inbound resolution path. {@code canActivate} hands connectors a {@code
   * ProcessElementContext} — and every successful {@code CorrelationResult} carries one — whose
   * public {@code getProperties()}/{@code bindProperties()} resolve secrets through their own
   * {@code SecretHandler}. Filtering only {@code springInboundConnectorContextFactory} leaves this
   * one allow-all, so a connector holding the activated element could still resolve a chained name
   * no model declares even under STRICT. This is the analogue of the {@code BindableProcessElement}
   * path #8538 filters upstream.
   *
   * <p>The probe has to be the chained case: this context resolves the element's own property text,
   * so any name written there is declared by definition and the allow-list is a superset of it.
   * What it must refuse is a name that only a resolved <em>value</em> spells — CHAIN_ROOT's value
   * is the literal {@code secrets.CHAINED}.
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
    assertEquals("secrets.CHAINED", resolveChainedViaActivatedElement(SecretFilterMode.STRICT));
    assertEquals("secrets.CHAINED", resolveChainedViaActivatedElement(SecretFilterMode.LAX));
    assertEquals("secrets.CHAINED", resolveChainedViaActivatedElement(SecretFilterMode.DISABLED));
  }

  private String resolveChainedViaActivatedElement(SecretFilterMode mode) {
    var elementFactory =
        configuration.processElementContextFactory(
            new ObjectMapper(),
            mock(ValidationProvider.class),
            new SecretProviderAggregator(List.of(chainingProvider())),
            mode);
    var handler =
        new InboundCorrelationHandler(
            mock(io.camunda.zeebe.client.ZeebeClient.class),
            new io.camunda.connector.feel.FeelEngineWrapper(),
            elementFactory,
            java.time.Duration.ofSeconds(60));
    var result = handler.canActivate(List.of(chainRootElement()), Map.of());
    var activated = ((ActivationCheckResult.Success.CanActivate) result).activatedElement();
    return (String) activated.getProperties().get("token");
  }

  private static InboundConnectorElement chainRootElement() {
    var properties =
        Map.of("inbound.type", "io.camunda:connector:1", "token", "{{secrets.CHAIN_ROOT}}");
    return new InboundConnectorElement(
        properties,
        new StandaloneMessageCorrelationPoint("", "", null, null),
        new ProcessElement("process", 0, 0, "id", "<default>"));
  }

  private static SecretProvider chainingProvider() {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return switch (name.trim()) {
          case "CHAIN_ROOT" -> "secrets.CHAINED";
          case "CHAINED" -> "leaked-value";
          default -> null;
        };
      }
    };
  }
}
