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
package io.camunda.connector.runtime.core.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.command.ResolveSecretsCommandStep1;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.client.api.response.SecretReference;
import io.camunda.client.api.search.enums.SecretErrorCode;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.feel.FeelExpressionEvaluatorBuilder;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.feel.jackson.JacksonModuleSecretReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Binds inbound properties through the whole chain — raw {@code zeebe:property} text, legacy
 * replacement, cluster evaluation, secret resolution — so that the pieces are exercised together
 * rather than only in isolation.
 */
@SuppressWarnings("unchecked")
class InboundSecretReferenceBindingTest {

  private final Map<String, String> secretStore = new HashMap<>();
  private final List<List<String>> resolveRequests = new ArrayList<>();
  private final Map<String, Object> evaluations = new HashMap<>();
  private final Map<String, List<String>> referencedSecrets = new HashMap<>();

  /** Mirrors a credential property class: some fields carry @FEEL, most do not. */
  record Credentials(String hmacSecret, @FEEL String token, Map<String, String> headers) {}

  @Test
  void resolvesAReferenceInAPropertyFeelNeverEvaluates() {
    secretStore.put("camunda.secrets.HMAC", "hmac-value");
    evaluationOf("=camunda.secrets.HMAC").returns("camunda.secrets.HMAC").referencing("HMAC");

    var bound = bind(Map.of("hmacSecret", "=camunda.secrets.HMAC"));

    assertThat(bound.hmacSecret()).isEqualTo("hmac-value");
  }

  @Test
  void resolvesAReferenceInsideACompositeAnnotatedProperty() {
    secretStore.put("camunda.secrets.TOKEN", "tok-1");
    evaluationOf("=\"Bearer \" + camunda.secrets.TOKEN")
        .returns("Bearer camunda.secrets.TOKEN")
        .referencing("TOKEN");

    var bound = bind(Map.of("token", "=\"Bearer \" + camunda.secrets.TOKEN"));

    assertThat(bound.token()).isEqualTo("Bearer tok-1");
  }

  @Test
  void resolvesAReferenceCarriedByAClusterVariable() {
    // A SECRET_REFERENCE cluster variable holds reference text in place of a value, so the
    // property itself names no reference at all — only the evaluation result does.
    secretStore.put("camunda.secrets.DB", "db-password");
    evaluationOf("=camunda.vars.cluster.dbCred").returns("camunda.secrets.DB").referencing("DB");

    var bound = bind(Map.of("token", "=camunda.vars.cluster.dbCred"));

    assertThat(bound.token()).isEqualTo("db-password");
  }

  @Test
  void leavesReferenceTextThatArrivedAsData() {
    // The same text, from a plain JSON cluster variable or a correlated payload: the cluster
    // reports no reference for it, so it stays literal.
    secretStore.put("camunda.secrets.DB", "db-password");
    evaluationOf("=camunda.vars.cluster.plainNote")
        .returns("camunda.secrets.DB")
        .referencingNothing();

    var bound = bind(Map.of("token", "=camunda.vars.cluster.plainNote"));

    assertThat(bound.token()).isEqualTo("camunda.secrets.DB");
    assertThat(resolveRequests).isEmpty();
  }

  @Test
  void resolvesAReferenceInAMapProperty() {
    secretStore.put("camunda.secrets.TOKEN", "tok-1");
    evaluationOf("=camunda.secrets.TOKEN").returns("camunda.secrets.TOKEN").referencing("TOKEN");

    var bound =
        bind(
            Map.of(
                "headers.Authorization", "=camunda.secrets.TOKEN",
                "headers.Accept", "application/json"));

    assertThat(bound.headers())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("Authorization", "tok-1", "Accept", "application/json"));
  }

  @Test
  void doesNotLetLegacyReplacementTouchANewFormReference() {
    // The legacy pass runs first, over raw model text, and holds a secret named TOKEN. It must not
    // rewrite the tail of camunda.secrets.TOKEN.
    secretStore.put("camunda.secrets.TOKEN", "central-value");
    evaluationOf("=camunda.secrets.TOKEN").returns("camunda.secrets.TOKEN").referencing("TOKEN");

    var bound =
        bind(Map.of("hmacSecret", "=camunda.secrets.TOKEN"), Map.of("TOKEN", "local-value"));

    assertThat(bound.hmacSecret()).isEqualTo("central-value");
  }

  @Test
  void makesNoResolveCallForPropertiesThatNameNoSecret() {
    evaluationOf("=camunda.vars.env.region").returns("eu-1").referencingNothing();

    var bound = bind(Map.of("token", "=camunda.vars.env.region", "hmacSecret", "plain"));

    assertThat(bound.token()).isEqualTo("eu-1");
    assertThat(bound.hmacSecret()).isEqualTo("plain");
    assertThat(resolveRequests).isEmpty();
  }

  @Test
  void leavesThePlaceholderWhenTheClusterCannotResolveIt() {
    evaluationOf("=camunda.secrets.MISSING")
        .returns("camunda.secrets.MISSING")
        .referencing("MISSING");

    var bound = bind(Map.of("hmacSecret", "=camunda.secrets.MISSING"));

    assertThat(bound.hmacSecret()).isEqualTo("camunda.secrets.MISSING");
  }

  private Credentials bind(Map<String, String> rawProperties) {
    return bind(rawProperties, Map.of());
  }

  private Credentials bind(Map<String, String> rawProperties, Map<String, String> legacySecrets) {
    var camundaClient = camundaClient();
    var objectMapper = objectMapper(camundaClient);
    var context =
        new InboundConnectorContextImpl(
            legacyProvider(legacySecrets),
            ignored -> {},
            details(rawProperties),
            null,
            e -> {},
            objectMapper,
            entry -> {},
            camundaClient);
    return context.bindProperties(Credentials.class);
  }

  /**
   * The inbound mapper as the runtime assembles it: the FEEL module for {@code @FEEL} fields, and
   * the secret-reference module for everything else.
   */
  private ObjectMapper objectMapper(CamundaClient camundaClient) {
    var evaluator = FeelExpressionEvaluatorBuilder.camundaClient(camundaClient).build();
    return ConnectorsObjectMapperSupplier.getCopy()
        .registerModules(
            new JacksonModuleFeelFunction(true, evaluator), new JacksonModuleSecretReference());
  }

  private static ValidInboundConnectorDetails details(Map<String, String> rawProperties) {
    var properties = new HashMap<>(rawProperties);
    properties.put("inbound.type", "io.camunda:connector:1");
    var element =
        new InboundConnectorElement(
            properties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElementWithRuntimeData(
                "bool",
                null,
                null,
                0,
                0,
                "id",
                null,
                null,
                "<default>",
                "engine-1",
                new ElementTemplateDetails("Test", "1", "icon"),
                properties));
    return (ValidInboundConnectorDetails)
        InboundConnectorDetails.of(element.deduplicationId(List.of()), List.of(element));
  }

  private static SecretProvider legacyProvider(Map<String, String> values) {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return values.get(name);
      }
    };
  }

  private CamundaClient camundaClient() {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    when(client.newEvaluateExpressionCommand().expression(any()))
        .thenAnswer(
            invocation -> {
              String expression = invocation.getArgument(0);
              var response = new StubEvaluation(expression);
              when(step2.send().join()).thenReturn(response);
              return step2;
            });

    var resolveCommand = mock(ResolveSecretsCommandStep1.class);
    var resolveFuture = mock(CamundaFuture.class);
    when(client.newResolveSecretsCommand()).thenReturn(resolveCommand);
    when(resolveCommand.references(org.mockito.ArgumentMatchers.anyList()))
        .thenAnswer(
            invocation -> {
              resolveRequests.add(List.copyOf(invocation.<List<String>>getArgument(0)));
              return resolveCommand;
            });
    when(resolveCommand.send()).thenReturn(resolveFuture);
    when(resolveFuture.join())
        .thenAnswer(
            invocation -> {
              var r = new StubResolution(resolveRequests.getLast(), secretStore);
              System.out.println(
                  "DBG join() resolved="
                      + r.getResolved().size()
                      + " errors="
                      + r.getErrors().size());
              return r;
            });
    return client;
  }

  private EvaluationBuilder evaluationOf(String expression) {
    return new EvaluationBuilder(expression);
  }

  private final class EvaluationBuilder {
    private final String expression;

    private EvaluationBuilder(String expression) {
      this.expression = expression;
    }

    private EvaluationBuilder returns(Object result) {
      evaluations.put(expression, result);
      return this;
    }

    private void referencing(String... names) {
      referencedSecrets.put(expression, List.of(names));
    }

    private void referencingNothing() {
      referencedSecrets.put(expression, List.of());
    }
  }

  private final class StubEvaluation implements EvaluateExpressionResponse {
    private final String expression;

    private StubEvaluation(String expression) {
      this.expression = expression;
    }

    @Override
    public String getExpression() {
      return expression;
    }

    @Override
    public Object getResult() {
      return evaluations.getOrDefault(expression, expression);
    }

    @Override
    public List<io.camunda.client.api.response.EvaluationWarning> getWarnings() {
      return List.of();
    }

    @Override
    public List<SecretReference> getReferencedSecrets() {
      System.out.println(
          "DBG getReferencedSecrets for ["
              + expression
              + "] -> "
              + referencedSecrets.get(expression));
      return referencedSecrets.getOrDefault(expression, List.of()).stream()
          .<SecretReference>map(StubSecretReference::new)
          .toList();
    }
  }

  private record StubSecretReference(String name) implements SecretReference {
    @Override
    public String getStoreId() {
      return "default";
    }

    @Override
    public String getSecretName() {
      return name;
    }
  }

  private record StubResolution(List<String> batch, Map<String, String> store)
      implements ResolveSecretsResponse {
    @Override
    public boolean isFullyResolved() {
      return getErrors().isEmpty();
    }

    @Override
    public List<ResolvedSecret> getResolved() {
      return batch.stream()
          .filter(store::containsKey)
          .<ResolvedSecret>map(
              reference ->
                  new ResolvedSecret() {
                    @Override
                    public String getReference() {
                      return reference;
                    }

                    @Override
                    public String getValue() {
                      return store.get(reference);
                    }
                  })
          .toList();
    }

    @Override
    public List<ResolutionError> getErrors() {
      return batch.stream()
          .filter(reference -> !store.containsKey(reference))
          .<ResolutionError>map(
              reference ->
                  new ResolutionError() {
                    @Override
                    public String getReference() {
                      return reference;
                    }

                    @Override
                    public SecretErrorCode getCode() {
                      return SecretErrorCode.NOT_FOUND;
                    }

                    @Override
                    public String getMessage() {
                      return "not found";
                    }
                  })
          .toList();
    }
  }
}
