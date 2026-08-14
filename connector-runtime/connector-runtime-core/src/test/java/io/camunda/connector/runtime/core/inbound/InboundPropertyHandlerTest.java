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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretHandler;
import io.camunda.connector.runtime.core.secret.SecretReferenceResolver;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class InboundPropertyHandlerTest {

  @Test
  void valid_1_level_properties() {
    // given
    var properties =
        Map.of(
            "foo", "bar",
            "baz", "bar");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThat(result).isEqualTo(properties);
  }

  @Test
  void valid_2_level_properties() {
    // given
    var properties =
        Map.of(
            "foo.bar", "baz",
            "foo.baz", "bar");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected =
        Map.of(
            "foo",
            Map.of(
                "bar", "baz",
                "baz", "bar"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void valid_3_level_properties() {
    // given
    var properties =
        Map.of(
            "foo.bar.baz", "baz",
            "foo.bar.baz2", "baz2",
            "foo.baz", "bar");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected =
        Map.of(
            "foo",
            Map.of(
                "bar",
                Map.of(
                    "baz", "baz",
                    "baz2", "baz2"),
                "baz",
                "bar"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void valid_mixed_level_properties() {
    // given
    var properties =
        Map.of(
            "foo", "baz",
            "bar.baz", "baz");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected = Map.of("foo", "baz", "bar", Map.of("baz", "baz"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void valid_withTrailingDot_noEmpty() {
    // given
    var properties = Map.of("bar.baz.", "baz");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected = Map.of("bar", Map.of("baz", "baz"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void invalid_duplicateKey_shorterFirst() {
    // given
    var properties =
        Map.of(
            "foo.bar", "baz",
            "foo.bar.baz", "baz");

    // when
    Supplier<Map<String, Object>> getResultLambda =
        () -> InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThrows(RuntimeException.class, getResultLambda::get);
  }

  @Test
  void invalid_duplicateKey_longerFirst() {
    // given
    var properties =
        Map.of(
            "foo.bar.baz", "baz",
            "foo.bar", "baz");

    // when
    Supplier<Map<String, Object>> getResultLambda =
        () -> InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThrows(RuntimeException.class, getResultLambda::get);
  }

  @Test
  void invalid_emptyPathPart() {
    // given
    var properties = Map.of("foo..bar", "baz");

    // when
    Supplier<Map<String, Object>> getResultLambda =
        () -> InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThrows(RuntimeException.class, getResultLambda::get);
  }

  // -- getPropertiesWithSecrets - shared by both inbound call sites:
  // InboundConnectorContextImpl#getPropertiesWithSecrets (connector-level properties) and
  // #bindElementProperties (element-scoped properties) both delegate to this one static method.

  @Test
  void getPropertiesWithSecrets_resolvesOldFormSecrets() {
    var objectMapper = new ObjectMapper();
    SecretProvider secretProvider =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            return "TOKEN".equals(name) ? "secret-value" : null;
          }
        };
    var secretHandler = new SecretHandler(secretProvider, SecretFilter.allowAll());
    Map<String, Object> properties = Map.of("auth", "{{secrets.TOKEN}}");

    var result =
        InboundPropertyHandler.getPropertiesWithSecrets(
            secretHandler, objectMapper, properties, new SecretContext("tenant", "process"));

    assertThat(result).isEqualTo(Map.of("auth", "secret-value"));
  }

  @Test
  void getPropertiesWithSecrets_resolvesNewFormSecrets() {
    var objectMapper = new ObjectMapper();
    SecretProvider secretProvider =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            return null;
          }
        };
    SecretReferenceResolver resolver =
        references -> Map.of("camunda.secrets.TOKEN", "resolved-value");
    var secretHandler = new SecretHandler(secretProvider, SecretFilter.allowAll(), resolver);
    Map<String, Object> properties = Map.of("auth", "camunda.secrets.TOKEN");

    var result =
        InboundPropertyHandler.getPropertiesWithSecrets(
            secretHandler, objectMapper, properties, null);

    assertThat(result).isEqualTo(Map.of("auth", "resolved-value"));
  }
}
