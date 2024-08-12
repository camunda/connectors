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
package io.camunda.connector.runtime.test;

import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;

public class ConnectorTestJobClientImpl implements JobClient {

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(long jobKey) {
    return new ConnectorTestCompleteJobCommandImpl(jobKey);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(ActivatedJob job) {
    return newCompleteCommand(job.getKey());
  }

  @Override
  public FailJobCommandStep1 newFailCommand(long jobKey) {
    return null;
  }

  @Override
  public FailJobCommandStep1 newFailCommand(ActivatedJob job) {
    return newFailCommand(job.getKey());
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(long jobKey) {
    return null;
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(ActivatedJob job) {
    return null;
  }

  @Override
  public ActivateJobsCommandStep1 newActivateJobsCommand() {
    return null;
  }

  @Override
  public StreamJobsCommandStep1 newStreamJobsCommand() {
    return null;
  }
}
