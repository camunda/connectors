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
package io.camunda.connector.runtime.core.inbound.correlation;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates activation conditions for inbound connector elements and determines which element(s)
 * should be activated for a given input context.
 */
public class ActivationConditionEvaluator {

  private static final Logger LOG = LoggerFactory.getLogger(ActivationConditionEvaluator.class);

  private final FeelEngineWrapper feelEngine;

  public ActivationConditionEvaluator(FeelEngineWrapper feelEngine) {
    this.feelEngine = feelEngine;
  }

  /**
   * Checks whether any of the provided elements can be activated for the given context.
   *
   * @param elements the connector elements to check
   * @param context the input context (variables from the inbound event)
   * @return the activation check result indicating success or failure
   */
  public ActivationCheckResult checkActivation(
      List<InboundConnectorElement> elements, Object context) {
    var matchingElements = getMatchingElements(elements, context);

    if (matchingElements.isEmpty()) {
      var discardUnmatchedEvents =
          elements.stream()
              .map(InboundConnectorElement::consumeUnmatchedEvents)
              .anyMatch(e -> e.equals(Boolean.TRUE));
      return new ActivationCheckResult.Failure.NoMatchingElement(discardUnmatchedEvents);
    }

    if (matchingElements.size() > 1) {
      // Multiple elements match - check if they are compatible message elements
      var compatibleElement = findCompatibleMessageElement(matchingElements);
      if (compatibleElement.isPresent()) {
        return new ActivationCheckResult.Success.CanActivate(compatibleElement.get().element());
      }
      return new ActivationCheckResult.Failure.TooManyMatchingElements();
    }

    return new ActivationCheckResult.Success.CanActivate(matchingElements.getFirst().element());
  }

  /**
   * Evaluates the activation condition for a single element.
   *
   * @param element the connector element
   * @param context the input context
   * @return true if the activation condition is met (or if no condition is specified)
   */
  public boolean isActivationConditionMet(InboundConnectorElement element, Object context) {
    var maybeCondition = element.activationCondition();
    if (maybeCondition == null || maybeCondition.isBlank()) {
      LOG.debug("No activation condition specified for connector");
      return true;
    }
    LOG.debug("Evaluating activation condition: {}", maybeCondition);
    try {
      Object shouldActivate = feelEngine.evaluate(maybeCondition, context);
      LOG.debug("Activation condition evaluated to: {}", shouldActivate);
      return Boolean.TRUE.equals(shouldActivate);
    } catch (FeelEngineWrapperException e) {
      throw new ConnectorInputException(e);
    }
  }

  private List<InboundConnectorElement> getMatchingElements(
      List<InboundConnectorElement> elements, Object context) {
    return elements.stream().filter(e -> isActivationConditionMet(e, context)).toList();
  }

  /**
   * Checks if multiple matching elements are compatible message elements that can be safely
   * correlated. Elements are compatible if:
   *
   * <ul>
   *   <li>All are message correlation points (intermediate catch events or message start events)
   *   <li>All have the same message name
   *   <li>All have the same resultExpression, resultVariable, and correlationKeyExpression
   * </ul>
   *
   * <p>When compatible, we can pick any one (the first) since they're functionally identical and
   * Zeebe will route the message correctly via the correlation key.
   *
   * @param matchingElements elements that matched the activation condition
   * @return the first element if all are compatible, empty otherwise
   */
  private Optional<InboundConnectorElement> findCompatibleMessageElement(
      List<InboundConnectorElement> matchingElements) {

    // Check all elements are message correlation points
    boolean allMessageElements =
        matchingElements.stream()
            .allMatch(e -> e.correlationPoint() instanceof MessageCorrelationPoint);
    if (!allMessageElements) {
      LOG.debug("Not all matching elements are message correlation points");
      return Optional.empty();
    }

    // Check all have the same message name
    var messageNames =
        matchingElements.stream()
            .map(e -> ((MessageCorrelationPoint) e.correlationPoint()).messageName())
            .distinct()
            .toList();
    if (messageNames.size() != 1) {
      LOG.debug("Multiple matching elements have different message names: {}", messageNames);
      return Optional.empty();
    }

    // Check compatibility of resultExpression, resultVariable, correlationKeyExpression
    var first = matchingElements.getFirst();
    String firstResultExpression = first.resultExpression();
    String firstResultVariable = first.resultVariable();
    String firstCorrelationKeyExpression =
        ((MessageCorrelationPoint) first.correlationPoint()).correlationKeyExpression();

    for (int i = 1; i < matchingElements.size(); i++) {
      var element = matchingElements.get(i);
      String correlationKeyExpression =
          ((MessageCorrelationPoint) element.correlationPoint()).correlationKeyExpression();

      if (!Objects.equals(firstResultExpression, element.resultExpression())
          || !Objects.equals(firstResultVariable, element.resultVariable())
          || !Objects.equals(firstCorrelationKeyExpression, correlationKeyExpression)) {
        LOG.debug(
            "Matching elements have incompatible properties. "
                + "Element {} has resultExpression={}, resultVariable={}, correlationKeyExpression={}. "
                + "Element {} has resultExpression={}, resultVariable={}, correlationKeyExpression={}",
            first.element().elementId(),
            firstResultExpression,
            firstResultVariable,
            firstCorrelationKeyExpression,
            element.element().elementId(),
            element.resultExpression(),
            element.resultVariable(),
            correlationKeyExpression);
        return Optional.empty();
      }
    }

    LOG.debug(
        "Found {} compatible message elements with message name '{}', using first one",
        matchingElements.size(),
        messageNames.getFirst());
    return Optional.of(first);
  }
}
