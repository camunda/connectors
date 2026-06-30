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
package io.camunda.connector.api.inbound;

import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import java.util.Map;

public sealed interface CorrelationResult {

  sealed interface Success extends CorrelationResult {

    ProcessElement activatedElement();

    /**
     * Binds the raw properties of the {@link #activatedElement()} to a typed object using the
     * runtime's secret-replacement and FEEL-evaluation pipeline.
     *
     * <p>Use this to resolve element-scoped properties (for example, a webhook response expression)
     * from the element that actually matched this correlation, even when several elements were
     * deduplicated into a single executable.
     *
     * <p><b>Warning:</b> by the time a {@link Success} exists, correlation has already taken effect
     * — the process instance was created or the message was published (i.e. this runs past the
     * transaction boundary). A failure while binding or evaluating these properties cannot undo
     * that, so callers must handle exceptions carefully and must not report the event as
     * unprocessed.
     */
    default <T> T bindProperties(Class<T> cls) {
      return activatedElement().bindProperties(cls);
    }

    record ProcessInstanceCreated(
        ProcessElement activatedElement, Long processInstanceKey, String tenantId)
        implements Success {}

    /**
     * Result for synchronous process instance creation via {@code createProcessInstanceWithResult}.
     * Contains the variables returned by the process instance upon completion.
     */
    record ProcessInstanceCreatedWithResult(
        ProcessElement activatedElement,
        Long processInstanceKey,
        String tenantId,
        Map<String, Object> variables)
        implements Success {}

    record MessagePublished(ProcessElement activatedElement, Long messageKey, String tenantId)
        implements Success {}

    /**
     * Result for synchronous message correlation via {@code newCorrelateMessageCommand}. Contains
     * the process instance key of the correlated process instance.
     */
    record MessageCorrelated(
        ProcessElement activatedElement, Long processInstanceKey, Long messageKey, String tenantId)
        implements Success {}

    record MessageAlreadyCorrelated(ProcessElement activatedElement) implements Success {}
  }

  sealed interface Failure extends CorrelationResult {

    String message();

    default CorrelationFailureHandlingStrategy handlingStrategy() {
      return ForwardErrorToUpstream.RETRYABLE;
    }

    record InvalidInput(String message, Throwable error) implements Failure {

      @Override
      public CorrelationFailureHandlingStrategy handlingStrategy() {
        return ForwardErrorToUpstream.NON_RETRYABLE;
      }
    }

    record ActivationConditionNotMet(boolean consumeUnmatched) implements Failure {

      @Override
      public String message() {
        return "Activation condition not met";
      }

      @Override
      public CorrelationFailureHandlingStrategy handlingStrategy() {
        if (consumeUnmatched) {
          return Ignore.INSTANCE;
        } else {
          return ForwardErrorToUpstream.NON_RETRYABLE;
        }
      }
    }

    record ZeebeClientStatus(String status, String message) implements Failure {}

    record Other(Throwable error) implements Failure {

      @Override
      public String message() {
        return error.getMessage();
      }
    }
  }
}
