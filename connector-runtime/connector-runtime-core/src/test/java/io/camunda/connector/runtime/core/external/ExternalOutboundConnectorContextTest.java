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
package io.camunda.connector.runtime.core.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExternalOutboundConnectorContextTest {

  @Mock private SecretProvider secretProvider;
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @Mock private ValidationProvider validationProvider;

  private record TestRecord(Integer key) {}

  private record NestedTestRecord(TestRecord nested) {}

  private record ArrayTestRecord(List<Integer> key) {}

  @Test
  void getVariablesAsTypeFromString() {
    var context =
        new ExternalOutboundConnectorContext(
            secretProvider, validationProvider, objectMapper, "{\"key\": 3}");
    TestRecord result = context.bindVariables(TestRecord.class);
    assertThat(result.key()).isEqualTo(3);
  }

  @Test
  void getVariablesAsTypeFromObject() {
    var context =
        new ExternalOutboundConnectorContext(
            secretProvider, validationProvider, objectMapper, Map.of("key", 3));
    TestRecord result = context.bindVariables(TestRecord.class);
    assertThat(result.key()).isEqualTo(3);
  }

  @Test
  void secretsAreReplaced() {
    var context =
        new ExternalOutboundConnectorContext(
            secretProvider, validationProvider, objectMapper, Map.of("key", "{{secrets.KEY}}"));
    when(secretProvider.getSecret("KEY")).thenReturn("5");
    TestRecord result = context.bindVariables(TestRecord.class);
    assertThat(result.key()).isEqualTo(5);
  }

  @Test
  void nestedSecretsAreReplaced() {
    var context =
        new ExternalOutboundConnectorContext(
            secretProvider,
            validationProvider,
            objectMapper,
            Map.of("nested", Map.of("key", "{{secrets.KEY}}")));
    when(secretProvider.getSecret("KEY")).thenReturn("5");
    NestedTestRecord result = context.bindVariables(NestedTestRecord.class);
    assertThat(result.nested().key()).isEqualTo(5);
  }

  @Test
  void arraySecretsAreReplaced() {
    var context =
        new ExternalOutboundConnectorContext(
            secretProvider,
            validationProvider,
            objectMapper,
            Map.of("key", List.of("{{secrets.KEY}}")));

    when(secretProvider.getSecret("KEY")).thenReturn("5");
    ArrayTestRecord result = context.bindVariables(ArrayTestRecord.class);
    assertThat(result.key()).containsExactly(5);
  }
}
