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
package io.camunda.connector.runtime.inbound.webhook;

import static io.camunda.connector.runtime.core.Keywords.RESPONSE_BODY_EXPRESSION_KEYWORD;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.impl.feel.FeelEngineWrapper;
import io.camunda.connector.impl.feel.FeelEngineWrapperException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class WebhookResponseMapper {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookResponseMapper.class);

  public static final String PROCESS_DATA_KEY = "processData";

  private final FeelEngineWrapper feelEngineWrapper;

  @Autowired
  public WebhookResponseMapper(final FeelEngineWrapper feelEngineWrapper) {
    this.feelEngineWrapper = feelEngineWrapper;
  }

  public ResponseEntity mapResponse(
      final InboundConnectorResult correlatedResult,
      final Map<String, Object> properties,
      final Object context) {
    final var responseBodyExpression =
        Optional.ofNullable(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD))
            .map(Object::toString)
            .orElse("");
    if (responseBodyExpression.isBlank()) {
      return ResponseEntity.ok(Map.of(PROCESS_DATA_KEY, correlatedResult));
    }

    Map evaluatedResponseBody =
        Optional.ofNullable(evaluateResponseBodyExpression(responseBodyExpression, context))
            .orElse(Collections.emptyMap());

    return ResponseEntity.ok(evaluatedResponseBody);
  }

  protected Map evaluateResponseBodyExpression(
      final String responseBodyExpression, final Object context) {
    if (responseBodyExpression.trim().length() == 0) {
      return Collections.emptyMap();
    }
    try {
      return feelEngineWrapper.evaluate(responseBodyExpression, context);
    } catch (FeelEngineWrapperException e) {
      throw new ConnectorInputException(e);
    }
  }
}
