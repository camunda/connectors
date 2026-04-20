/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.JobCompletionListener;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AiAgentResponseListenerDelegationTest {

  @Nested
  class TaskResponseTests {

    @Test
    void onJobCompleted_delegatesToListener() {
      var listener = mock(JobCompletionListener.class);
      var response = new AiAgentTaskConnectorResponse(null, listener);

      response.onJobCompleted();

      verify(listener).onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_delegatesToListener() {
      var listener = mock(JobCompletionListener.class);
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
  class SubProcessResponseTests {

    @Test
    void onJobCompleted_delegatesToListener() {
      var listener = mock(JobCompletionListener.class);
      var response = buildResponse(listener);

      response.onJobCompleted();

      verify(listener).onJobCompleted();
    }

    @Test
    void onJobCompletionFailed_delegatesToListener() {
      var listener = mock(JobCompletionListener.class);
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

    private AiAgentSubProcessConnectorResponse buildResponse(JobCompletionListener listener) {
      return AiAgentSubProcessConnectorResponse.builder()
          .variables(Map.of())
          .elementActivations(List.of())
          .completionConditionFulfilled(false)
          .cancelRemainingInstances(false)
          .completionListener(listener)
          .build();
    }
  }
}
