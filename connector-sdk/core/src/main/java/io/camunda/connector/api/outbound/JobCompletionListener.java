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

/**
 * Callbacks for job completion outcomes. Connector responses can implement this interface to
 * receive notification after the Zeebe command is dispatched.
 *
 * <p>For the completeJob path, callbacks fire asynchronously after the command future resolves. For
 * failJob and throwBpmnError paths (triggered by error expressions), callbacks fire synchronously
 * before the command is dispatched, since the outcome is already determined.
 */
public interface JobCompletionListener {

  /** Called when the job was successfully completed (completeJob command accepted by Zeebe). */
  void onJobCompleted();

  /**
   * Called when the job was not successfully completed. This covers command failures, superseded
   * jobs, error expression failures, and BPMN errors.
   */
  void onJobCompletionFailed(JobCompletionFailure failure);
}
