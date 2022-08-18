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

package io.camunda.connector.awslambda.model;

import com.amazonaws.services.lambda.model.InvokeResult;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class AwsLambdaResult {

  private Integer statusCode;
  private String executedVersion;
  private String payload;

  public AwsLambdaResult(final InvokeResult invokeResult) {
    this.statusCode = invokeResult.getStatusCode();
    this.executedVersion = invokeResult.getExecutedVersion();
    this.payload =
        Optional.ofNullable(invokeResult.getPayload())
            .map(ByteBuffer::array)
            .map(byteArray -> new String(byteArray, StandardCharsets.UTF_8))
            .orElse(null);
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(final Integer statusCode) {
    this.statusCode = statusCode;
  }

  public String getExecutedVersion() {
    return executedVersion;
  }

  public void setExecutedVersion(final String executedVersion) {
    this.executedVersion = executedVersion;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(final String payload) {
    this.payload = payload;
  }

  @Override
  public String toString() {
    return "AwsLambdaResult{"
        + "statusCode="
        + statusCode
        + ", executedVersion='"
        + executedVersion
        + "'"
        + ", payload='"
        + payload
        + "'"
        + "}";
  }
}
