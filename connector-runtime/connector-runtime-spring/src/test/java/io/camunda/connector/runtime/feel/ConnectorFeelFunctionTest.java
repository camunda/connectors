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
package io.camunda.connector.runtime.feel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.runtime.core.discovery.SPIConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.secret.SecretProviderDiscovery;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.List;
import org.camunda.feel.context.FunctionProvider;
import org.junit.jupiter.api.Test;

public class ConnectorFeelFunctionTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  private final FunctionProvider functionProvider =
      new ConnectorInvocationFeelFunctionProvider(
          objectMapper,
          new DefaultOutboundConnectorFactory(SPIConnectorDiscovery.discoverOutbound()),
          new SecretProviderAggregator(SecretProviderDiscovery.discoverSecretProviders()),
          new DefaultValidationProvider());

  private final FeelEngineWrapper feelEngineWrapper =
      new FeelEngineWrapper(List.of(functionProvider));

  @Test
  public void executeRestConnector() {
    // given
    String feelScript =
        "= connector(\"io.camunda:http-json:1\", { url: \"https://example.com\", method: \"GET\" })";

    // when
    Object result = feelEngineWrapper.evaluate(feelScript);

    // then
    var httpCommonResult = objectMapper.convertValue(result, HttpCommonResult.class);
    assertThat(httpCommonResult.getStatus()).isEqualTo(200);
  }
}
