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
import io.camunda.connector.api.inbound.ActivationCheckResult.Failure.NoMatchingElement;
import io.camunda.connector.api.inbound.ActivationCheckResult.Failure.TooManyMatchingElements;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates activation conditions for inbound connector elements and determines which element(s)
 * should be activated for a given input context.
 *
 * <p>The elements of an executable can belong to several versions of a process. An element that
 * can't be evaluated against the input is left out, whatever its version, so that it can't block
 * the others.
 */
public class ActivationConditionEvaluator {

  private static final Logger LOG = LoggerFactory.getLogger(ActivationConditionEvaluator.class);

  private static final Comparator<InboundConnectorElement> LATEST_VERSION_FIRST =
      Comparator.comparingInt((InboundConnectorElement e) -> e.element().version()).reversed();

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
   *
   * <p>Elements publishing the same message (same name and correlation key expression) must be
   * compatible and are correlated once, with the latest version's element. Elements publishing
   * different messages are all correlated: Zeebe routes each message to its own subscriptions.
   */
  ActivationResolution resolveActivation(List<InboundConnectorElement> elements, Object context) {
    var matching = getMatchingElements(elements, context);

    var elementsToCorrelate = new ArrayList<InboundConnectorElement>();
    for (var sameMessage : groupByMessage(matching.elements())) {
      var incompatibility = incompatibility(sameMessage);
      if (incompatibility == null) {
        elementsToCorrelate.add(Collections.min(sameMessage, LATEST_VERSION_FIRST));
      } else if (sameMessage.stream().allMatch(e -> isOlderVersion(e, elements))) {
        LOG.warn("Skipping incompatible elements of older versions: {}", incompatibility);
      } else {
        return ActivationResolution.failure(
            new TooManyMatchingElements(withRemediation(incompatibility, sameMessage)));
      }
    }

    if (elementsToCorrelate.isEmpty()) {
      var latestVersionError = matching.latestVersionError();
      if (latestVersionError != null) {
        throw latestVersionError;
      }
      return ActivationResolution.failure(new NoMatchingElement(consumesUnmatchedEvents(elements)));
    }
    if (elementsToCorrelate.size() > 1
        && elementsToCorrelate.stream().anyMatch(InboundConnectorElement::synchronousResponse)) {
      return ActivationResolution.failure(
          new TooManyMatchingElements("A synchronous response cannot come from several messages"));
    }
    elementsToCorrelate.sort(LATEST_VERSION_FIRST);
    return ActivationResolution.success(elementsToCorrelate);
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
   * An element whose activation condition can't be evaluated doesn't match. The latest version's
   * error is kept, to fail the input if nothing else can be correlated.
   */
  private Matching getMatchingElements(List<InboundConnectorElement> elements, Object context) {
    var matchingElements = new ArrayList<InboundConnectorElement>();
    ConnectorInputException latestVersionError = null;
    for (var element : elements) {
      try {
        if (isActivationConditionMet(element, context)) {
          matchingElements.add(element);
        }
      } catch (ConnectorInputException e) {
        // the cause is not logged, it can contain input values
        LOG.warn(
            "Skipping element '{}' (version {}): its activation condition cannot be evaluated",
            element.element().elementId(),
            element.element().version());
        if (latestVersionError == null && !isOlderVersion(element, elements)) {
          latestVersionError = e;
        }
      }
    }
    return new Matching(matchingElements, latestVersionError);
  }

  /**
   * Groups elements by the message they publish: catch and boundary events by name and correlation
   * key expression. Start events are their own group: they start an instance rather than wake a
   * waiting one.
   */
  private static Collection<List<InboundConnectorElement>> groupByMessage(
      List<InboundConnectorElement> elements) {
    return elements.stream()
        .collect(
            Collectors.groupingBy(
                e ->
                    e.correlationPoint() instanceof MessageCorrelationPoint point
                        ? new Message(point.messageName(), point.correlationKeyExpression())
                        : e,
                LinkedHashMap::new,
                Collectors.toList()))
        .values();
  }

  /**
   * Why elements publishing the same message can't be correlated as one, or {@code null} if they
   * can: they must also publish the same variables, message ID and time to live.
   */
  private static @Nullable String incompatibility(List<InboundConnectorElement> sameMessage) {
    if (sameMessage.size() < 2) {
      return null;
    }
    var mismatches = new ArrayList<String>();
    addIfDifferent(mismatches, "resultExpression", sameMessage, e -> e.resultExpression());
    addIfDifferent(mismatches, "resultVariable", sameMessage, e -> e.resultVariable());
    addIfDifferent(
        mismatches, "messageIdExpression", sameMessage, e -> message(e).messageIdExpression());
    addIfDifferent(mismatches, "timeToLive", sameMessage, e -> message(e).timeToLive());
    return mismatches.isEmpty() ? null : formatIncompatibilityReason(sameMessage, mismatches);
  }

  private static void addIfDifferent(
      List<String> mismatches,
      String property,
      List<InboundConnectorElement> elements,
      Function<InboundConnectorElement, @Nullable Object> value) {
    var values = elements.stream().map(e -> String.valueOf(value.apply(e))).distinct().toList();
    if (values.size() > 1) {
      mismatches.add(property + ": " + values);
    }
  }

  private static MessageCorrelationPoint message(InboundConnectorElement element) {
    return (MessageCorrelationPoint) element.correlationPoint();
  }

  private static String formatIncompatibilityReason(
      List<InboundConnectorElement> elements, List<String> mismatches) {
    var versions = elements.stream().map(e -> e.element().version()).distinct().toList();
    var describedElements =
        versions.size() == 1
            ? elements.stream()
                    .map(e -> "'" + e.element().elementId() + "'")
                    .distinct()
                    .collect(Collectors.joining(", "))
                + " (version %d)".formatted(versions.getFirst())
            : elements.stream()
                .map(
                    e ->
                        "'%s' (version %d)"
                            .formatted(e.element().elementId(), e.element().version()))
                .distinct()
                .collect(Collectors.joining(", "));
    return "Elements %s have incompatible properties: %s"
        .formatted(describedElements, String.join(", ", mismatches));
  }

  /**
   * Adds what to do when the conflict spans process versions: the older version stays active, and
   * blocks the input, as long as its instances wait for the message.
   */
  private static String withRemediation(String reason, List<InboundConnectorElement> sameMessage) {
    if (sameMessage.stream().map(e -> e.element().version()).distinct().count() < 2) {
      return reason;
    }
    return reason
        + ". Deploying a new version does not resolve this while instances of the older versions"
        + " wait for the message: migrate or cancel them, or disable active-version polling"
        + " (camunda.connector.polling.active-versions-enabled=false)";
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

  static boolean consumesUnmatchedEvents(List<InboundConnectorElement> elements) {
    return elements.stream()
        .map(InboundConnectorElement::consumeUnmatchedEvents)
        .anyMatch(Boolean.TRUE::equals);
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

  private record Matching(
      List<InboundConnectorElement> elements,
      @Nullable ConnectorInputException latestVersionError) {}

  /** A message as published by an element: its name and correlation key expression. */
  private record Message(String name, String correlationKeyExpression) {}
}
