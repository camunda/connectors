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

import org.jspecify.annotations.Nullable;

/**
 * Optional callbacks for job completion outcomes. An {@link OutboundConnectorFunction} can
 * implement this interface to receive notification after the Zeebe command is dispatched.
 *
 * <p>Callbacks fire asynchronously after the corresponding Zeebe command future ({@code
 * completeJob}, {@code failJob}, or {@code throwBpmnError}) resolves. The associated {@link
 * JobCompletionFailure} subtype reflects the path that produced the outcome, with an optional
 * {@code commandFailure} field surfacing the case where the response command was itself rejected by
 * Zeebe.
 */
public interface JobCompletionListener {

  /**
   * Called when the job was successfully completed (completeJob command accepted by Zeebe).
   *
   * @param context the connector context for the job
   * @param response the connector response that was used to complete the job
   */
  void onJobCompleted(OutboundConnectorContext context, ConnectorResponse response);

  /**
   * Called when the job was not successfully completed. This covers command failures, superseded
   * jobs, error expression failures, BPMN errors, and pre-response failures (variable
   * binding/validation/{@code execute()} throwing).
   *
   * <p>The {@code response} argument is {@code null} when no response was produced (e.g. the
   * connector function threw before returning).
   *
   * @param context the connector context for the job
   * @param response the connector response, or {@code null} if no response was produced
   * @param failure the failure that caused job completion to fail
   */
  void onJobCompletionFailed(
      OutboundConnectorContext context,
      @Nullable ConnectorResponse response,
      JobCompletionFailure failure);
}
