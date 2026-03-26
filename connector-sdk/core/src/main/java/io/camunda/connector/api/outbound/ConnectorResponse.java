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
package io.camunda.connector.api.outbound;

import java.util.List;
import java.util.Map;

/**
 * Base interface for connector responses returned from {@link OutboundConnectorFunction#execute}.
 * The runtime uses {@link #responseValue()} as input for result expression evaluation.
 *
 * <p>Implementations can override {@link #getVariables(Map)} to control which variables are sent
 * with the job completion command.
 */
public sealed interface ConnectorResponse {

  /**
   * Returns the raw response value. The runtime evaluates the result expression against this value
   * to produce the output variables.
   *
   * @return the response value, or {@code null}
   */
  Object responseValue();

  /**
   * Returns the variables to send with the job completion command. The runtime passes the variables
   * computed from result expression evaluation; implementations may use, replace, or merge them.
   *
   * <p>The default implementation returns the result expression variables unchanged.
   *
   * @param resultVariables variables computed from result expression evaluation
   * @return the variables to use for job completion
   */
  default Map<String, Object> getVariables(Map<String, Object> resultVariables) {
    return resultVariables;
  }

  /**
   * Indicates whether an {@code IgnoreError} from error expression evaluation should be rejected.
   * When {@code true}, the job will be failed instead of completed.
   *
   * <p>The default implementation returns {@code false}.
   *
   * @return {@code true} to reject {@code IgnoreError} and fail the job
   */
  default boolean rejectIgnoreError() {
    return false;
  }

  /** Standard connector response for completing a job with the result expression variables. */
  non-sealed interface StandardConnectorResponse extends ConnectorResponse {

    /**
     * Creates a connector response wrapping the given value.
     *
     * @param responseValue the raw response value for result expression evaluation
     * @return a new {@link StandardConnectorResponse}
     */
    static StandardConnectorResponse of(Object responseValue) {
      return () -> responseValue;
    }
  }

  /**
   * Connector response for completing a job within an ad-hoc sub-process. The runtime translates
   * this into a Zeebe complete command with ad-hoc sub-process result configuration.
   *
   * <p>Implementations provide the elements to activate, completion condition, and cancellation
   * flags. The runtime builds the complete command accordingly.
   */
  non-sealed interface AdHocSubProcessConnectorResponse extends ConnectorResponse {

    /**
     * Returns the elements to activate in the ad-hoc sub-process.
     *
     * @return the element activations, or an empty list if no elements should be activated
     */
    List<ElementActivation> elementActivations();

    /**
     * Indicates whether the completion condition of the ad-hoc sub-process is fulfilled.
     *
     * @return {@code true} if the completion condition is fulfilled
     */
    boolean completionConditionFulfilled();

    /**
     * Indicates whether all remaining instances of the ad-hoc sub-process should be canceled.
     *
     * @return {@code true} to cancel remaining instances
     */
    boolean cancelRemainingInstances();

    /** An element to activate within an ad-hoc sub-process, with optional scoped variables. */
    interface ElementActivation {

      /**
       * Returns the identifier of the element to activate.
       *
       * @return the element ID
       */
      String elementId();

      /**
       * Returns the variables to create on the activated element instance.
       *
       * <p>The default implementation returns an empty map.
       *
       * @return the variables for the activated element
       */
      default Map<String, Object> variables() {
        return Map.of();
      }
    }
  }
}
