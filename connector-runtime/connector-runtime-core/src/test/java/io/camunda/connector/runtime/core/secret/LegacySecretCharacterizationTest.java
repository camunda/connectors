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
package io.camunda.connector.runtime.core.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.InboundPropertyHandler;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins the behaviour of the legacy secret machinery — {@code {{secrets.X}}} and bare {@code
 * secrets.X}, read from the locally configured secret providers — as it stands before centralized
 * secret resolution is added. These tests must pass unchanged before and after that work; that they
 * still do is the evidence the legacy path did not shift under it.
 *
 * <p>No input here contains {@code camunda.secrets.}. Where the two forms overlap in text the
 * behaviour does change deliberately, and that change is asserted in {@code SecretUtilTests} rather
 * than pinned here.
 */
class LegacySecretCharacterizationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ObjectNode wrap(String value) {
    return objectMapper.createObjectNode().put("value", value);
  }

  @ParameterizedTest
  @CsvSource({
    "secrets.test,test, true",
    "secrets.TEST,TEST, true",
    "secrets.A/B,A/B, true",
    "secrets.A.B,A.B, true",
    "{secrets.TEST},TEST, true",
    "secrets.TEST0,TEST0, true",
    "secrets.TEST-0,TEST-0, true",
    "secrets.TEST_0,TEST_0, true",
    "secrets.TEST_TEST,TEST_TEST, true",
    "secrets.a_b_c_d_e_f,a_b_c_d_e_f, true",
    "secrets.a.b.c.d.e.f,a.b.c.d.e.f, true",
    "secrets.TEST TEST,TEST,true",
    "secrets._TEST,,false",
    "secrets./TEST,,false",
    "secrets.-TEST,,false",
    "secrets..TEST,,false",
    "secrets.,,false",
    "secrets..,,false",
    "secrets.?,,false"
  })
  void secretNameCharsetIsUnchanged(String input, String secret, Boolean shouldDetect) {
    var secretReplacer = mock(SecretReplacer.class);

    SecretUtil.replaceSecrets(wrap(input), null, secretReplacer);

    if (shouldDetect) {
      verify(secretReplacer).replaceSecrets(eq(new Secret(secret, List.of("value"))), any());
    } else {
      verifyNoInteractions(secretReplacer);
    }
  }

  @Test
  void bothSpellingsAreReplacedInOneJsonPayload() throws Exception {
    Map<String, String> secrets = Map.of("K1", "V1", "K2", "V2", "K3", "V3");
    SecretReplacer secretReplacer = (secret, context) -> secrets.get(secret.secretName());
    String input = "{\"a\":{\"b\":\"secrets.K1\"},\"c\":[\"{{secrets.K2}}\",\"secrets.K3\"]}";

    var result =
        SecretUtil.replaceSecrets((ObjectNode) objectMapper.readTree(input), null, secretReplacer);

    assertThat(result)
        .isEqualTo(objectMapper.readTree("{\"a\":{\"b\":\"V1\"},\"c\":[\"V2\",\"V3\"]}"));
  }

  @Test
  void aReferenceNestedInsideAnArrayOfArraysIsReplaced() throws Exception {
    Map<String, String> secrets = Map.of("TOKEN", "tok123");
    SecretReplacer secretReplacer = (secret, context) -> secrets.get(secret.secretName());
    String input = "{\"values\":[[\"{{secrets.TOKEN}}\"]]}";

    var result =
        SecretUtil.replaceSecrets((ObjectNode) objectMapper.readTree(input), null, secretReplacer);

    assertThat(result).isEqualTo(objectMapper.readTree("{\"values\":[[\"tok123\"]]}"));
  }

  @Test
  void aReferenceWrittenAsAPropertyNameIsReplacedJustAsOneWrittenAsAValueIs() throws Exception {
    // The raw-text pass this replaced matched a placeholder anywhere in the document, key or
    // value, since it never parsed the document's structure. A placeholder used as a property
    // name has to keep resolving the same way for the tree-walking replacement to be a faithful
    // successor.
    Map<String, String> secrets = Map.of("KEY", "resolved-key");
    SecretReplacer secretReplacer = (secret, context) -> secrets.get(secret.secretName());
    String input = "{\"{{secrets.KEY}}\":\"value\"}";

    var result =
        SecretUtil.replaceSecrets((ObjectNode) objectMapper.readTree(input), null, secretReplacer);

    assertThat(result).isEqualTo(objectMapper.readTree("{\"resolved-key\":\"value\"}"));
  }

  @Test
  void aRenamedKeyCollidingWithALaterNonTextPropertyLosesToIt() throws Exception {
    // "{{secrets.KEY}}" resolves to "resolved-key", which collides with a property that already
    // exists further along in the same object. Parsing raw JSON text with a duplicate key keeps
    // the later one, so the walk has to reproduce that: the later property's own value -- here an
    // object, not a plain string -- must win, not the earlier (renamed) one.
    Map<String, String> secrets = Map.of("KEY", "resolved-key");
    SecretReplacer secretReplacer = (secret, context) -> secrets.get(secret.secretName());
    String input = "{\"{{secrets.KEY}}\":\"placeholder\",\"resolved-key\":{\"nested\":\"value\"}}";

    var result =
        SecretUtil.replaceSecrets((ObjectNode) objectMapper.readTree(input), null, secretReplacer);

    assertThat(result)
        .isEqualTo(objectMapper.readTree("{\"resolved-key\":{\"nested\":\"value\"}}"));
  }

  @Test
  void aReferenceEmbeddedInALongerValueIsReplaced() {
    Map<String, String> secrets = Map.of("TOKEN", "tok123", "A", "a1", "B", "b2");
    SecretReplacer secretReplacer = (secret, context) -> secrets.get(secret.secretName());

    assertThat(
            SecretUtil.replaceSecrets(wrap("Bearer {{secrets.TOKEN}}"), null, secretReplacer)
                .get("value")
                .asText())
        .isEqualTo("Bearer tok123");
    assertThat(
            SecretUtil.replaceSecrets(wrap("{{secrets.A}}/{{secrets.B}}"), null, secretReplacer)
                .get("value")
                .asText())
        .isEqualTo("a1/b2");
  }

  @Test
  void theSameSecretIsReplacedAtEveryOccurrence() {
    SecretReplacer secretReplacer =
        (secret, context) -> "K".equals(secret.secretName()) ? "V" : null;

    var result =
        SecretUtil.replaceSecrets(wrap("secrets.K secrets.K {{secrets.K}}"), null, secretReplacer);

    assertThat(result.get("value").asText()).isEqualTo("V V V");
  }

  @Test
  void whitespaceInsideBracesIsTolerated() {
    SecretReplacer secretReplacer =
        (secret, context) -> "X".equals(secret.secretName()) ? "val" : null;

    assertThat(
            SecretUtil.replaceSecrets(wrap("{{ secrets.X }}"), null, secretReplacer)
                .get("value")
                .asText())
        .isEqualTo("val");
  }

  @Test
  void aRefusedSecretLeavesItsPlaceholderAndDoesNotThrow() {
    Map<String, String> secrets = Map.of("KEY1", "VALUE1", "KEY2", "VALUE2", "KEY3", "VALUE3");
    List<String> allowList = List.of("KEY1", "KEY2");
    SecretReplacer secretReplacer =
        (secret, context) ->
            allowList.contains(secret.secretName()) ? secrets.get(secret.secretName()) : null;
    String content = "Hello {{secrets.KEY1}} and {{secrets.KEY2}} and {{secrets.KEY3}}";

    var result =
        SecretUtil.replaceSecrets(
            wrap(content), new SecretContext("tenantId", "processId"), secretReplacer);

    assertThat(result.get("value").asText())
        .isEqualTo("Hello VALUE1 and VALUE2 and {{secrets.KEY3}}");
  }

  @Test
  void aMissingSecretFailsTheConnector() {
    SecretHandler secretHandler = new SecretHandler(mapProvider(Map.of()), SecretFilter.allowAll());

    assertThatThrownBy(() -> secretHandler.replaceSecrets(wrap("{{secrets.MISSING}}"), null))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessage("Secret with name 'MISSING' is not available");
  }

  @Test
  void aValueNeedingJsonEscapingSurvivesTheRoundTrip() throws Exception {
    // A quote, a backslash, a carriage return and a NUL byte in the resolved value. The NUL is
    // written as an escape rather than as a raw byte: a raw NUL makes git treat the whole file as
    // binary, which hides every later change to it from review.
    String raw = "quote\"back\\slash\rcarriage\0null";
    SecretHandler secretHandler =
        new SecretHandler(mapProvider(Map.of("SECRET", raw)), SecretFilter.allowAll());

    var output = secretHandler.replaceSecrets(wrap("secrets.SECRET"), null);
    var roundTripped = objectMapper.readTree(objectMapper.writeValueAsString(output));

    assertThat(roundTripped.get("value").asText()).isEqualTo(raw);
  }

  @Test
  void theConnectorContextResolvesThroughTheSameHandler() {
    ValidationProvider validationProvider = ignored -> {};
    var context =
        new AbstractConnectorContext(
            mapProvider(Map.of("FOO", "bar-value")),
            SecretFilter.allowAll(),
            validationProvider) {};

    var result =
        context
            .getSecretHandler()
            .replaceSecrets(wrap("{{secrets.FOO}}"), new SecretContext("t", "p"));

    assertThat(result.get("value").asText()).isEqualTo("bar-value");
  }

  @Test
  void theFirstProviderHoldingANameWins() {
    var aggregator =
        new SecretProviderAggregator(
            List.of(
                mapProvider(Map.of()),
                mapProvider(Map.of("X", "second-X")),
                mapProvider(Map.of("X", "third-X"))));

    assertThat(aggregator.getSecret("X", null)).isEqualTo("second-X");
  }

  @Test
  void inboundPropertyBindingReplacesBeforeAnythingElseSeesTheProperties() {
    SecretHandler secretHandler =
        new SecretHandler(mapProvider(Map.of("TOKEN", "tok-1")), SecretFilter.allowAll());
    Map<String, Object> properties =
        InboundPropertyHandler.readWrappedProperties(
            Map.of("auth.header", "Bearer {{secrets.TOKEN}}", "auth.bare", "secrets.TOKEN"));

    var withSecrets =
        InboundPropertyHandler.getPropertiesWithSecrets(
            secretHandler, objectMapper, properties, new SecretContext("tenant", "process"));

    assertThat(withSecrets)
        .isEqualTo(Map.of("auth", Map.of("header", "Bearer tok-1", "bare", "tok-1")));
  }

  @Test
  void inboundPropertyBindingCarriesThePhysicalTenantOnEveryLookup() {
    var recorded = new ArrayList<SecretContext>();
    SecretProvider recordingProvider =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            recorded.add(context);
            return "value";
          }
        };
    SecretHandler secretHandler = new SecretHandler(recordingProvider, SecretFilter.allowAll());
    var context = new SecretContext("tenant", "process", "engine-1");

    InboundPropertyHandler.getPropertiesWithSecrets(
        secretHandler,
        objectMapper,
        InboundPropertyHandler.readWrappedProperties(Map.of("token", "{{secrets.TOKEN}}")),
        context);

    assertThat(recorded).singleElement().isEqualTo(context);
  }

  private static SecretProvider mapProvider(Map<String, String> values) {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return values.get(name);
      }
    };
  }
}
