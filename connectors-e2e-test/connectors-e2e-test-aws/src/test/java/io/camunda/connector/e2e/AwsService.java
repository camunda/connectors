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
package io.camunda.connector.e2e;

import org.testcontainers.containers.localstack.LocalStackContainer;

public enum AwsService implements LocalStackContainer.EnabledService {
  DYNAMODB("dynamodb", 4569),
  LAMBDA("lambda", 4574),
  SNS("sns", 4575),
  SQS("sqs", 4576),
  EVENTBRIDGE("events", 4566);

  private final String localStackName;
  private final int port;

  AwsService(String localStackName, int port) {
    this.localStackName = localStackName;
    this.port = port;
  }

  @Override
  public String getName() {
    return localStackName;
  }

  @Override
  public int getPort() {
    return port;
  }
}
