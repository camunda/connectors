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
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates activation conditions for inbound connector elements and determines which element(s)
 * should be activated for a given input context.
 */
public class ActivationConditionEvaluator {

  private static final Logger LOG = LoggerFactory.getLogger(ActivationConditionEvaluator.class);

  private final FeelExpressionEvaluator feelExpressionEvaluator;

  public ActivationConditionEvaluator(FeelExpressionEvaluator feelExpressionEvaluator) {
    this.feelExpressionEvaluator = feelExpressionEvaluator;
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
    return resolveActivation(elements, context).result();
  }

  /**
   * Same as {@link #checkActivation}, but also returns the elements the input must be correlated
   * to: one element per distinct message, latest version first.
   */
  ActivationResolution resolveActivation(List<InboundConnectorElement> elements, Object context) {
    var matchingElements = getMatchingElements(elements, context);

    if (matchingElements.isEmpty()) {
      var discardUnmatchedEvents =
          elements.stream()
              .map(InboundConnectorElement::consumeUnmatchedEvents)
              .anyMatch(e -> e.equals(Boolean.TRUE));
      return ActivationResolution.failure(
          new ActivationCheckResult.Failure.NoMatchingElement(discardUnmatchedEvents));
    }

    if (matchingElements.size() > 1) {
      // Elements publishing the same message (same name and correlation key expression) must be
      // compatible and are correlated once; elements publishing different messages are all
      // correlated, Zeebe routes each message to its own subscriptions.
      var elementsByMessage =
          matchingElements.stream()
              .collect(
                  Collectors.groupingBy(
                      ActivationConditionEvaluator::messageOf,
                      LinkedHashMap::new,
                      Collectors.toList()));
      var elementsToCorrelate = new ArrayList<InboundConnectorElement>();
      for (var sameMessage : elementsByMessage.values()) {
        if (sameMessage.size() > 1) {
          var compatibilityResult = checkMessageElementCompatibility(sameMessage);
          if (!compatibilityResult.compatible()) {
            return ActivationResolution.failure(
                new ActivationCheckResult.Failure.TooManyMatchingElements(
                    Objects.requireNonNull(compatibilityResult.reason())));
          }
        }
        elementsToCorrelate.add(
            sameMessage.stream().max(Comparator.comparingInt(e -> e.element().version())).get());
      }

      if (elementsToCorrelate.size() > 1
          && elementsToCorrelate.stream().anyMatch(InboundConnectorElement::synchronousResponse)) {
        return ActivationResolution.failure(
            new ActivationCheckResult.Failure.TooManyMatchingElements(
                "A synchronous response cannot come from several messages"));
      }
      elementsToCorrelate.sort(
          Comparator.comparingInt((InboundConnectorElement e) -> e.element().version()).reversed());
      return ActivationResolution.success(elementsToCorrelate);
    }

    return ActivationResolution.success(List.of(matchingElements.getFirst()));
  }

  /**
   * The message an element publishes, as its name and correlation key expression. Elements
   * publishing no message (plain start events) are their own key.
   */
  private static Object messageOf(InboundConnectorElement element) {
    return switch (element.correlationPoint()) {
      case MessageCorrelationPoint point ->
          new Message(point.messageName(), point.correlationKeyExpression());
      case MessageStartEventCorrelationPoint point ->
          new Message(point.messageName(), point.correlationKeyExpression());
      case null, default -> element;
    };
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
      Object shouldActivate = feelExpressionEvaluator.evaluate(maybeCondition, context);
      LOG.debug("Activation condition evaluated to: {}", shouldActivate);
      return Boolean.TRUE.equals(shouldActivate);
    } catch (FeelEngineWrapperException e) {
      throw new ConnectorInputException(
          "Activation condition could not be evaluated: %s".formatted(e.getMessage()), e);
    }
  }

  /**
   * An older version whose activation condition cannot be evaluated is left out rather than failing
   * the input, so it cannot block the latest version.
   */
  private List<InboundConnectorElement> getMatchingElements(
      List<InboundConnectorElement> elements, Object context) {
    var matchingElements = new ArrayList<InboundConnectorElement>();
    for (var element : elements) {
      try {
        if (isActivationConditionMet(element, context)) {
          matchingElements.add(element);
        }
      } catch (ConnectorInputException e) {
        if (!isOlderVersion(element, elements)) {
          throw e;
        }
        // the cause is not logged, it can contain input values
        LOG.warn(
            "Skipping element '{}' (version {}): its activation condition cannot be evaluated",
            element.element().elementId(),
            element.element().version());
      }
    }
    return matchingElements;
  }

  /**
   * Whether the executable holds a newer process version than the element's. Determined from all
   * elements of the executable, not only the matching ones.
   */
  static boolean isOlderVersion(
      InboundConnectorElement element, List<InboundConnectorElement> elements) {
    return elements.stream()
        .anyMatch(other -> other.element().version() > element.element().version());
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

    // Check compatibility of all publish-relevant properties using distinct count
    var mismatches = new java.util.ArrayList<String>();

    var resultExpressions =
        matchingElements.stream()
            .map(InboundConnectorElement::resultExpression)
            .distinct()
            .toList();
    if (resultExpressions.size() > 1) {
      mismatches.add("resultExpression: " + resultExpressions);
    }

    var resultVariables =
        matchingElements.stream().map(InboundConnectorElement::resultVariable).distinct().toList();
    if (resultVariables.size() > 1) {
      mismatches.add("resultVariable: " + resultVariables);
    }

    var correlationKeyExpressions =
        matchingElements.stream()
            .map(e -> ((MessageCorrelationPoint) e.correlationPoint()).correlationKeyExpression())
            .distinct()
            .toList();
    if (correlationKeyExpressions.size() > 1) {
      mismatches.add("correlationKeyExpression: " + correlationKeyExpressions);
    }

    var messageIdExpressions =
        matchingElements.stream()
            .map(e -> ((MessageCorrelationPoint) e.correlationPoint()).messageIdExpression())
            .distinct()
            .toList();
    if (messageIdExpressions.size() > 1) {
      mismatches.add("messageIdExpression: " + messageIdExpressions);
    }

    var timeToLives =
        matchingElements.stream()
            .map(e -> ((MessageCorrelationPoint) e.correlationPoint()).timeToLive())
            .distinct()
            .toList();
    if (timeToLives.size() > 1) {
      mismatches.add("timeToLive: " + timeToLives);
    }

    if (!mismatches.isEmpty()) {
      var reason = formatIncompatibilityReason(matchingElements, mismatches);
      LOG.debug(reason);
      return CompatibilityResult.incompatible(reason);
    }

    LOG.debug(
        "Found {} compatible message elements with message name '{}', using first one",
        matchingElements.size(),
        messageNames.getFirst());
    return CompatibilityResult.compatible(matchingElements.getFirst());
  }

  private String formatIncompatibilityReason(
      List<InboundConnectorElement> elements, java.util.ArrayList<String> mismatches) {
    var mismatchDetails = String.join(", ", mismatches);
    var versions = elements.stream().map(e -> e.element().version()).distinct().toList();
    var elementIds =
        elements.stream()
            .map(e -> "'" + e.element().elementId() + "'")
            .distinct()
            .collect(java.util.stream.Collectors.joining(", "));

    if (versions.size() == 1) {
      // Same version - mention version once
      return "Elements %s (version %d) have incompatible properties: %s"
          .formatted(elementIds, versions.getFirst(), mismatchDetails);
    } else {
      // Different versions - list elements with their versions
      var elementsWithVersions =
          elements.stream()
              .map(
                  e ->
                      "'%s' (version %d)".formatted(e.element().elementId(), e.element().version()))
              .distinct()
              .collect(java.util.stream.Collectors.joining(", "));
      return "Elements %s have incompatible properties: %s"
          .formatted(elementsWithVersions, mismatchDetails);
    }
  }

  /**
   * The outcome of {@link #resolveActivation}: the activation check result, and on success the
   * elements to correlate (latest version first).
   */
  record ActivationResolution(
      ActivationCheckResult result, List<InboundConnectorElement> elementsToCorrelate) {
    static ActivationResolution success(List<InboundConnectorElement> elementsToCorrelate) {
      return new ActivationResolution(
          new ActivationCheckResult.Success.CanActivate(elementsToCorrelate.getFirst().element()),
          elementsToCorrelate);
    }

    static ActivationResolution failure(ActivationCheckResult.Failure failure) {
      return new ActivationResolution(failure, List.of());
    }
  }

  /** A message as published by an element: its name and correlation key expression. */
  private record Message(String name, String correlationKeyExpression) {}

  private record CompatibilityResult(
      boolean compatible, @Nullable InboundConnectorElement element, @Nullable String reason) {
    static CompatibilityResult compatible(InboundConnectorElement element) {
      return new CompatibilityResult(true, element, null);
    }

    static CompatibilityResult incompatible(String reason) {
      return new CompatibilityResult(false, null, reason);
    }
  }
}
