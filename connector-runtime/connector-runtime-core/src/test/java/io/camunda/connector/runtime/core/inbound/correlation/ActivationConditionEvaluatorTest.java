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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.feel.LocalFeelEngineWrapper;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ActivationConditionEvaluator}, focusing on the smart compatibility check.
 */
public class ActivationConditionEvaluatorTest {

  private ActivationConditionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    evaluator = new ActivationConditionEvaluator(new LocalFeelEngineWrapper());
  }

  private InboundConnectorElement createMessageElement(
      String elementId,
      String messageName,
      String activationCondition,
      String resultExpression,
      String resultVariable,
      String correlationKeyExpression) {
    return createMessageElement(
        elementId,
        messageName,
        activationCondition,
        resultExpression,
        resultVariable,
        correlationKeyExpression,
        null,
        Duration.ofHours(1));
  }

  private InboundConnectorElement createMessageElement(
      String elementId,
      String messageName,
      String activationCondition,
      String resultExpression,
      String resultVariable,
      String correlationKeyExpression,
      String messageIdExpression,
      Duration timeToLive) {
    var element = mock(InboundConnectorElement.class);
    var correlationPoint =
        new StandaloneMessageCorrelationPoint(
            messageName, correlationKeyExpression, messageIdExpression, timeToLive);
    when(element.correlationPoint()).thenReturn(correlationPoint);
    when(element.element())
        .thenReturn(new ProcessElementWithRuntimeData("process1", 0, 0, elementId, "default"));
    when(element.activationCondition()).thenReturn(activationCondition);
    when(element.resultExpression()).thenReturn(resultExpression);
    when(element.resultVariable()).thenReturn(resultVariable);
    return element;
  }

  private InboundConnectorElement createStartEventElement(
      String elementId, String activationCondition) {
    var element = mock(InboundConnectorElement.class);
    var correlationPoint = new StartEventCorrelationPoint("process1", 0, 0);
    when(element.correlationPoint()).thenReturn(correlationPoint);
    when(element.element())
        .thenReturn(new ProcessElementWithRuntimeData("process1", 0, 0, elementId, "default"));
    when(element.activationCondition()).thenReturn(activationCondition);
    return element;
  }

  @Nested
  @DisplayName("Single element scenarios")
  class SingleElement {

    @Test
    @DisplayName("Single element with matching condition should activate")
    void singleElement_matchingCondition_shouldActivate() {
      var element = createMessageElement("elem1", "msg1", "=true", null, null, "=key");

      var result = evaluator.checkActivation(List.of(element), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Success.CanActivate.class);
    }

    @Test
    @DisplayName("Single element with non-matching condition should not activate")
    void singleElement_nonMatchingCondition_shouldNotActivate() {
      var element = createMessageElement("elem1", "msg1", "=false", null, null, "=key");

      var result = evaluator.checkActivation(List.of(element), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.NoMatchingElement.class);
    }

    @Test
    @DisplayName("Single element with blank condition should activate")
    void singleElement_blankCondition_shouldActivate() {
      var element = createMessageElement("elem1", "msg1", "", null, null, "=key");

      var result = evaluator.checkActivation(List.of(element), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Success.CanActivate.class);
    }
  }

  @Nested
  @DisplayName("Multiple elements with different activation conditions")
  class MultipleElementsDifferentConditions {

    @Test
    @DisplayName("Multiple elements where only one matches should activate that one")
    void multipleElements_oneMatches_shouldActivateThatOne() {
      var element1 = createMessageElement("elem1", "msg1", "=type = \"A\"", null, null, "=key");
      var element2 = createMessageElement("elem2", "msg2", "=type = \"B\"", null, null, "=key");

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of("type", "A"));

      assertThat(result).isInstanceOf(ActivationCheckResult.Success.CanActivate.class);
      var success = (ActivationCheckResult.Success.CanActivate) result;
      assertThat(success.activatedElement().elementId()).isEqualTo("elem1");
    }
  }

  @Nested
  @DisplayName("Compatible message elements (same message name, same properties)")
  class CompatibleMessageElements {

    @Test
    @DisplayName("Two message elements with same name and same properties should be compatible")
    void twoElements_sameMessageName_sameProperties_shouldBeCompatible() {
      var element1 =
          createMessageElement(
              "elem1", "shared-msg", "", "=result", "outputVar", "=correlationKey");
      var element2 =
          createMessageElement(
              "elem2", "shared-msg", "", "=result", "outputVar", "=correlationKey");

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Success.CanActivate.class);
    }

    @Test
    @DisplayName("Three message elements with same name and same properties should be compatible")
    void threeElements_sameMessageName_sameProperties_shouldBeCompatible() {
      var element1 =
          createMessageElement(
              "elem1", "shared-msg", "", "=result", "outputVar", "=correlationKey");
      var element2 =
          createMessageElement(
              "elem2", "shared-msg", "", "=result", "outputVar", "=correlationKey");
      var element3 =
          createMessageElement(
              "elem3", "shared-msg", "", "=result", "outputVar", "=correlationKey");

      var result = evaluator.checkActivation(List.of(element1, element2, element3), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Success.CanActivate.class);
    }

    @Test
    @DisplayName("Elements with null properties should be compatible if all null")
    void elements_nullProperties_shouldBeCompatibleIfAllNull() {
      var element1 = createMessageElement("elem1", "shared-msg", "", null, null, null);
      var element2 = createMessageElement("elem2", "shared-msg", "", null, null, null);

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Success.CanActivate.class);
    }

    @Test
    @DisplayName("Elements with same messageIdExpression and timeToLive should be compatible")
    void elements_sameMessageIdAndTtl_shouldBeCompatible() {
      var element1 =
          createMessageElement(
              "elem1",
              "shared-msg",
              "",
              "=result",
              "outputVar",
              "=correlationKey",
              "=msgId",
              Duration.ofMinutes(30));
      var element2 =
          createMessageElement(
              "elem2",
              "shared-msg",
              "",
              "=result",
              "outputVar",
              "=correlationKey",
              "=msgId",
              Duration.ofMinutes(30));

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Success.CanActivate.class);
    }
  }

  @Nested
  @DisplayName("Incompatible message elements (same message name, different properties)")
  class IncompatibleMessageElements {

    @Test
    @DisplayName("Different resultExpression should be incompatible")
    void differentResultExpression_shouldBeIncompatible() {
      var element1 =
          createMessageElement("elem1", "shared-msg", "", "=result.v1", "outputVar", "=key");
      var element2 =
          createMessageElement("elem2", "shared-msg", "", "=result.v2", "outputVar", "=key");

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("Different resultVariable should be incompatible")
    void differentResultVariable_shouldBeIncompatible() {
      var element1 = createMessageElement("elem1", "shared-msg", "", "=result", "var1", "=key");
      var element2 = createMessageElement("elem2", "shared-msg", "", "=result", "var2", "=key");

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("Different correlationKeyExpression should be incompatible")
    void differentCorrelationKeyExpression_shouldBeIncompatible() {
      var element1 = createMessageElement("elem1", "shared-msg", "", "=result", "var", "=key1");
      var element2 = createMessageElement("elem2", "shared-msg", "", "=result", "var", "=key2");

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("One null and one non-null resultExpression should be incompatible")
    void oneNullOneNonNull_resultExpression_shouldBeIncompatible() {
      var element1 = createMessageElement("elem1", "shared-msg", "", null, "var", "=key");
      var element2 = createMessageElement("elem2", "shared-msg", "", "=result", "var", "=key");

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("Different messageIdExpression should be incompatible")
    void differentMessageIdExpression_shouldBeIncompatible() {
      var element1 =
          createMessageElement(
              "elem1", "shared-msg", "", "=result", "var", "=key", "=msgId1", Duration.ofHours(1));
      var element2 =
          createMessageElement(
              "elem2", "shared-msg", "", "=result", "var", "=key", "=msgId2", Duration.ofHours(1));

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("One null and one non-null messageIdExpression should be incompatible")
    void oneNullOneNonNull_messageIdExpression_shouldBeIncompatible() {
      var element1 =
          createMessageElement(
              "elem1", "shared-msg", "", "=result", "var", "=key", null, Duration.ofHours(1));
      var element2 =
          createMessageElement(
              "elem2", "shared-msg", "", "=result", "var", "=key", "=msgId", Duration.ofHours(1));

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("Different timeToLive should be incompatible")
    void differentTimeToLive_shouldBeIncompatible() {
      var element1 =
          createMessageElement(
              "elem1", "shared-msg", "", "=result", "var", "=key", null, Duration.ofHours(1));
      var element2 =
          createMessageElement(
              "elem2", "shared-msg", "", "=result", "var", "=key", null, Duration.ofHours(2));

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("One null and one non-null timeToLive should be incompatible")
    void oneNullOneNonNull_timeToLive_shouldBeIncompatible() {
      var element1 =
          createMessageElement("elem1", "shared-msg", "", "=result", "var", "=key", null, null);
      var element2 =
          createMessageElement(
              "elem2", "shared-msg", "", "=result", "var", "=key", null, Duration.ofHours(1));

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }
  }

  @Nested
  @DisplayName("Different message names (always incompatible when both match)")
  class DifferentMessageNames {

    @Test
    @DisplayName("Different message names with blank conditions should be incompatible")
    void differentMessageNames_blankConditions_shouldBeIncompatible() {
      var element1 = createMessageElement("elem1", "msg-A", "", "=result", "var", "=key");
      var element2 = createMessageElement("elem2", "msg-B", "", "=result", "var", "=key");

      var result = evaluator.checkActivation(List.of(element1, element2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }
  }

  @Nested
  @DisplayName("Mixed correlation point types")
  class MixedCorrelationPointTypes {

    @Test
    @DisplayName("Mix of message and start event elements should be incompatible")
    void mixedTypes_shouldBeIncompatible() {
      var messageElement = createMessageElement("elem1", "msg1", "", "=result", "var", "=key");
      var startElement = createStartEventElement("elem2", "");

      var result = evaluator.checkActivation(List.of(messageElement, startElement), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }

    @Test
    @DisplayName("Two start event elements with blank conditions should be incompatible")
    void twoStartEvents_blankConditions_shouldBeIncompatible() {
      var startElement1 = createStartEventElement("elem1", "");
      var startElement2 = createStartEventElement("elem2", "");

      var result = evaluator.checkActivation(List.of(startElement1, startElement2), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.TooManyMatchingElements.class);
    }
  }

  @Nested
  @DisplayName("consumeUnmatchedEvents flag")
  class ConsumeUnmatchedEvents {

    @Test
    @DisplayName("No matching elements with consumeUnmatchedEvents=true should return flag")
    void noMatch_consumeUnmatchedTrue_shouldReturnFlag() {
      var element = createMessageElement("elem1", "msg1", "=false", null, null, "=key");
      when(element.consumeUnmatchedEvents()).thenReturn(true);

      var result = evaluator.checkActivation(List.of(element), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.NoMatchingElement.class);
      var failure = (ActivationCheckResult.Failure.NoMatchingElement) result;
      assertThat(failure.discardUnmatchedEvents()).isTrue();
    }

    @Test
    @DisplayName("No matching elements with consumeUnmatchedEvents=false should return flag")
    void noMatch_consumeUnmatchedFalse_shouldReturnFlag() {
      var element = createMessageElement("elem1", "msg1", "=false", null, null, "=key");
      when(element.consumeUnmatchedEvents()).thenReturn(false);

      var result = evaluator.checkActivation(List.of(element), Map.of());

      assertThat(result).isInstanceOf(ActivationCheckResult.Failure.NoMatchingElement.class);
      var failure = (ActivationCheckResult.Failure.NoMatchingElement) result;
      assertThat(failure.discardUnmatchedEvents()).isFalse();
    }
  }
}
