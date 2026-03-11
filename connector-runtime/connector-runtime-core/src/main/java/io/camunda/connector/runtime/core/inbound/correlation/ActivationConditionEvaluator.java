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
      var compatibilityResult = checkMessageElementCompatibility(matchingElements);
      if (compatibilityResult.compatible()) {
        return new ActivationCheckResult.Success.CanActivate(
            compatibilityResult.element().element());
      }
      return new ActivationCheckResult.Failure.TooManyMatchingElements(
          compatibilityResult.reason());
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
   *   <li>All are intermediate catch events
   *   <li>All have the same message name
   *   <li>All have the same resultExpression, resultVariable, correlationKeyExpression,
   *       messageIdExpression, and timeToLive
   * </ul>
   *
   * <p>When compatible, we can pick any one (the first) since they're functionally identical and
   * Zeebe will route the message correctly via the correlation key.
   *
   * @param matchingElements elements that matched the activation condition
   * @return the compatibility result with either the element to use or the reason for
   *     incompatibility
   */
  private CompatibilityResult checkMessageElementCompatibility(
      List<InboundConnectorElement> matchingElements) {

    // Check all elements are message correlation points
    boolean allMessageElements =
        matchingElements.stream()
            .allMatch(e -> e.correlationPoint() instanceof MessageCorrelationPoint);
    if (!allMessageElements) {
      var reason = "Not all matching elements are message correlation points";
      LOG.debug(reason);
      return CompatibilityResult.incompatible(reason);
    }

    // Check all have the same message name
    var messageNames =
        matchingElements.stream()
            .map(e -> ((MessageCorrelationPoint) e.correlationPoint()).messageName())
            .distinct()
            .toList();
    if (messageNames.size() != 1) {
      var reason = "Multiple matching elements have different message names: " + messageNames;
      LOG.debug(reason);
      return CompatibilityResult.incompatible(reason);
    }

    // Check compatibility of all publish-relevant properties
    var first = matchingElements.getFirst();
    var firstCorrelationPoint = (MessageCorrelationPoint) first.correlationPoint();
    String firstResultExpression = first.resultExpression();
    String firstResultVariable = first.resultVariable();
    String firstCorrelationKeyExpression = firstCorrelationPoint.correlationKeyExpression();
    String firstMessageIdExpression = firstCorrelationPoint.messageIdExpression();
    var firstTimeToLive = firstCorrelationPoint.timeToLive();

    for (int i = 1; i < matchingElements.size(); i++) {
      var element = matchingElements.get(i);
      var correlationPoint = (MessageCorrelationPoint) element.correlationPoint();

      var mismatches = new java.util.ArrayList<String>();
      if (!Objects.equals(firstResultExpression, element.resultExpression())) {
        mismatches.add(
            "resultExpression: '%s' vs '%s'"
                .formatted(firstResultExpression, element.resultExpression()));
      }
      if (!Objects.equals(firstResultVariable, element.resultVariable())) {
        mismatches.add(
            "resultVariable: '%s' vs '%s'"
                .formatted(firstResultVariable, element.resultVariable()));
      }
      if (!Objects.equals(
          firstCorrelationKeyExpression, correlationPoint.correlationKeyExpression())) {
        mismatches.add(
            "correlationKeyExpression: '%s' vs '%s'"
                .formatted(
                    firstCorrelationKeyExpression, correlationPoint.correlationKeyExpression()));
      }
      if (!Objects.equals(firstMessageIdExpression, correlationPoint.messageIdExpression())) {
        mismatches.add(
            "messageIdExpression: '%s' vs '%s'"
                .formatted(firstMessageIdExpression, correlationPoint.messageIdExpression()));
      }
      if (!Objects.equals(firstTimeToLive, correlationPoint.timeToLive())) {
        mismatches.add(
            "timeToLive: '%s' vs '%s'".formatted(firstTimeToLive, correlationPoint.timeToLive()));
      }

      if (!mismatches.isEmpty()) {
        var reason = formatIncompatibilityReason(first, element, mismatches);
        LOG.debug(reason);
        return CompatibilityResult.incompatible(reason);
      }
    }

    LOG.debug(
        "Found {} compatible message elements with message name '{}', using first one",
        matchingElements.size(),
        messageNames.getFirst());
    return CompatibilityResult.compatible(first);
  }

  private String formatIncompatibilityReason(
      InboundConnectorElement first,
      InboundConnectorElement second,
      java.util.ArrayList<String> mismatches) {
    var firstElement = first.element();
    var secondElement = second.element();
    var mismatchDetails = String.join(", ", mismatches);

    if (firstElement.version() == secondElement.version()) {
      // Same version - mention version once
      return "Elements '%s' and '%s' (version %d) have incompatible properties: %s"
          .formatted(
              firstElement.elementId(),
              secondElement.elementId(),
              firstElement.version(),
              mismatchDetails);
    } else {
      // Different versions - mention both
      return "Element '%s' (version %d) and element '%s' (version %d) have incompatible properties: %s"
          .formatted(
              firstElement.elementId(),
              firstElement.version(),
              secondElement.elementId(),
              secondElement.version(),
              mismatchDetails);
    }
  }

  private record CompatibilityResult(
      boolean compatible, InboundConnectorElement element, String reason) {
    static CompatibilityResult compatible(InboundConnectorElement element) {
      return new CompatibilityResult(true, element, null);
    }

    static CompatibilityResult incompatible(String reason) {
      return new CompatibilityResult(false, null, reason);
    }
  }
}
