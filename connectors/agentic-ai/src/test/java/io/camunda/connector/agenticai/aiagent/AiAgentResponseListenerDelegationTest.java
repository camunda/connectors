/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.connector.agenticai.aiagent.agent.AgentJobCompletionListener;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.ConnectorResponse.StandardConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AiAgentResponseListenerDelegationTest {

  @Nested
  class TaskResponseDelegationTests {

    @Test
    void onJobCompleted_delegatesToListener() {
      var listener = mock(AgentJobCompletionListener.class);
      var response = new AiAgentTaskConnectorResponse(null, listener);

      response.onJobCompleted();

      verify(listener).onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_delegatesToListener() {
      var listener = mock(AgentJobCompletionListener.class);
      var failure = new JobCompletionFailure.CommandFailed(new RuntimeException("test"));
      var response = new AiAgentTaskConnectorResponse(null, listener);

      response.onJobCompletionFailed(failure);

      verify(listener).onJobCompletionFailed(failure);
    }

    @Test
    void onJobCompleted_noOpWithNullListener() {
      var response = new AiAgentTaskConnectorResponse(null, null);

      // should not throw
      response.onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_noOpWithNullListener() {
      var response = new AiAgentTaskConnectorResponse(null, null);

      // should not throw
      response.onJobCompletionFailed(
          new JobCompletionFailure.CommandFailed(new RuntimeException("test")));
    }
  }

  @Nested
  class SubProcessResponseDelegationTests {

    @Test
    void onJobCompleted_delegatesToListener() {
      var listener = mock(AgentJobCompletionListener.class);
      var response = buildResponse(listener);

      response.onJobCompleted();

      verify(listener).onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_delegatesToListener() {
      var listener = mock(AgentJobCompletionListener.class);
      var failure = new JobCompletionFailure.CommandFailed(new RuntimeException("test"));
      var response = buildResponse(listener);

      response.onJobCompletionFailed(failure);

      verify(listener).onJobCompletionFailed(failure);
    }

    @Test
    void onJobCompleted_noOpWithNullListener() {
      var response = buildResponse(null);

      // should not throw
      response.onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_noOpWithNullListener() {
      var response = buildResponse(null);

      // should not throw
      response.onJobCompletionFailed(
          new JobCompletionFailure.CommandFailed(new RuntimeException("test")));
    }

    private AiAgentSubProcessConnectorResponse buildResponse(AgentJobCompletionListener listener) {
      return AiAgentSubProcessConnectorResponse.builder()
          .variables(Map.of())
          .elementActivations(List.of())
          .completionConditionFulfilled(false)
          .cancelRemainingInstances(false)
          .completionListener(listener)
          .build();
    }
  }

  @Nested
  class TaskFunctionDelegationTests {

    @Test
    void onJobCompleted_invokesAgentListenerCarriedByResponse() {
      var listener = mock(AgentJobCompletionListener.class);
      var function = new AiAgentFunction(null, null);
      var response = new AiAgentTaskConnectorResponse(null, listener);

      function.onJobCompleted(mock(OutboundConnectorContext.class), response);

      verify(listener).onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_invokesAgentListenerCarriedByResponse() {
      var listener = mock(AgentJobCompletionListener.class);
      var function = new AiAgentFunction(null, null);
      var response = new AiAgentTaskConnectorResponse(null, listener);
      var failure = new JobCompletionFailure.CommandFailed(new RuntimeException("test"));

      function.onJobCompletionFailed(mock(OutboundConnectorContext.class), response, failure);

      verify(listener).onJobCompletionFailed(failure);
    }

    @Test
    void onJobCompletionFailed_noOpWhenResponseIsNull() {
      var function = new AiAgentFunction(null, null);

      // pre-response failure (e.g. execute() threw): no response to delegate to
      function.onJobCompletionFailed(
          mock(OutboundConnectorContext.class),
          null,
          new JobCompletionFailure.CommandFailed(new RuntimeException("boom")));
    }

    @Test
    void onJobCompletionFailed_noOpWhenResponseIsNotAgentResponse() {
      var function = new AiAgentFunction(null, null);
      var foreignResponse = StandardConnectorResponse.of(Map.of("foo", "bar"));

      // should not throw
      function.onJobCompletionFailed(
          mock(OutboundConnectorContext.class),
          foreignResponse,
          new JobCompletionFailure.CommandFailed(new RuntimeException("boom")));
    }
  }

  @Nested
  class JobWorkerFunctionDelegationTests {

    @Test
    void onJobCompleted_invokesAgentListenerCarriedByResponse() {
      var listener = mock(AgentJobCompletionListener.class);
      var function = new AiAgentJobWorker(null);
      var response =
          AiAgentSubProcessConnectorResponse.builder()
              .variables(Map.of())
              .elementActivations(List.of())
              .completionConditionFulfilled(false)
              .cancelRemainingInstances(false)
              .completionListener(listener)
              .build();

      function.onJobCompleted(mock(OutboundConnectorContext.class), response);

      verify(listener).onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_invokesAgentListenerCarriedByResponse() {
      var listener = mock(AgentJobCompletionListener.class);
      var function = new AiAgentJobWorker(null);
      var response =
          AiAgentSubProcessConnectorResponse.builder()
              .variables(Map.of())
              .elementActivations(List.of())
              .completionConditionFulfilled(false)
              .cancelRemainingInstances(false)
              .completionListener(listener)
              .build();
      var failure = new JobCompletionFailure.CommandFailed(new RuntimeException("test"));

      function.onJobCompletionFailed(mock(OutboundConnectorContext.class), response, failure);

      verify(listener).onJobCompletionFailed(failure);
    }

    @Test
    void onJobCompletionFailed_noOpWhenResponseIsNull() {
      var function = new AiAgentJobWorker(null);
      var listener = mock(AgentJobCompletionListener.class);

      function.onJobCompletionFailed(
          mock(OutboundConnectorContext.class),
          (ConnectorResponse) null,
          new JobCompletionFailure.CommandFailed(new RuntimeException("boom")));

      verifyNoInteractions(listener);
    }
  }
}
