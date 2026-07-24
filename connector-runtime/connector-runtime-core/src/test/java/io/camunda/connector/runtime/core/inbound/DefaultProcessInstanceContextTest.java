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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultProcessInstanceContextTest {

  private final ObjectMapper mapper = TestObjectMapperSupplier.INSTANCE;

  @Test
  void bind_shouldUseCamundaClientEvaluatorWithElementInstanceKeyAsScopeKey() {
    // given
    var intermediateContext = mock(InboundIntermediateConnectorContextImpl.class);
    when(intermediateContext.getProperties()).thenReturn(Map.of("str", "= anything"));
    when(intermediateContext.getDefinition())
        .thenReturn(
            new InboundConnectorDefinition("type", "tenant-A", "dedup", java.util.List.of(), null));

    var elementInstance = mock(ElementInstance.class);
    when(elementInstance.getElementInstanceKey()).thenReturn(987654321L);
    when(elementInstance.getProcessInstanceKey()).thenReturn(111L);

    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    var response = mock(EvaluateExpressionResponse.class);
    when(camundaClient.newEvaluateExpressionCommand().expression(any())).thenReturn(step2);
    when(step2.send().join()).thenReturn(response);
    when(response.getResult()).thenReturn("scoped-result");

    ValidationProvider validationProvider = obj -> {};

    var context =
        new DefaultProcessInstanceContext(
            intermediateContext, elementInstance, validationProvider, null, mapper, camundaClient);

    // when
    SimpleProps result = context.bind(SimpleProps.class);

    // then
    assertThat(result.getStr()).isEqualTo("scoped-result");
    verify(step2).tenantId(eq("tenant-A"));
    verify(step2).scopeKey(eq(987654321L));
  }

  @Test
  void bind_shouldIncludeTenantAndScopeKeyWhenFeelBindingFails() {
    // given
    var intermediateContext = mock(InboundIntermediateConnectorContextImpl.class);
    when(intermediateContext.getProperties()).thenReturn(Map.of("str", "= failing"));
    when(intermediateContext.getDefinition())
        .thenReturn(
            new InboundConnectorDefinition("type", "tenant-A", "dedup", java.util.List.of(), null));

    var elementInstance = mock(ElementInstance.class);
    when(elementInstance.getElementInstanceKey()).thenReturn(987654321L);

    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    when(camundaClient.newEvaluateExpressionCommand().expression(any())).thenReturn(step2);
    when(step2.send().join()).thenThrow(new RuntimeException("remote FEEL failed"));

    ValidationProvider validationProvider = obj -> {};

    var context =
        new DefaultProcessInstanceContext(
            intermediateContext, elementInstance, validationProvider, null, mapper, camundaClient);

    // when/then
    assertThatThrownBy(() -> context.bind(SimpleProps.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to bind process instance properties")
        .hasMessageContaining(SimpleProps.class.getName())
        .hasMessageContaining("tenantId=tenant-A")
        .hasMessageContaining("scopeKey=987654321")
        .hasRootCauseMessage("remote FEEL failed");
  }

  public static class SimpleProps {
    @FEEL private String str;

    public String getStr() {
      return str;
    }

    public void setStr(String str) {
      this.str = str;
    }
  }
}
