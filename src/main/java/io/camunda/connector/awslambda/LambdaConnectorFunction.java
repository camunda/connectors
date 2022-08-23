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

package io.camunda.connector.awslambda;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.awslambda.model.AwsLambdaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LambdaConnectorFunction implements ConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(LambdaConnectorFunction.class);
  private final AwsLambdaSupplier awsLambdaSupplier;

  public LambdaConnectorFunction() {
    awsLambdaSupplier = new AwsLambdaSupplier();
  }

  public LambdaConnectorFunction(final AwsLambdaSupplier awsLambdaSupplier) {
    this.awsLambdaSupplier = awsLambdaSupplier;
  }

  @Override
  public Object execute(ConnectorContext context) {
    var request = context.getVariablesAsType(AwsLambdaRequest.class);
    LOGGER.info("Executing my connector with request {}", request);
    context.validate(request);
    context.replaceSecrets(request);
    return new AwsLambdaResult(invokeLambdaFunction(request));
  }

  private InvokeResult invokeLambdaFunction(AwsLambdaRequest request) {
    final AWSLambda awsLambda =
        awsLambdaSupplier.awsLambdaService(
            request.getAuthentication(), request.getFunction().getRegion());
    final InvokeRequest invokeRequest =
        new InvokeRequest()
            .withFunctionName(request.getFunction().getFunctionName())
            .withPayload(request.getFunction().getPayload());
    try {
      return awsLambda.invoke(invokeRequest);
    } finally {
      if (awsLambda != null) {
        awsLambda.shutdown();
      }
    }
  }
}
