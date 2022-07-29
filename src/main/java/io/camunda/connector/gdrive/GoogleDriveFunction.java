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
package io.camunda.connector.gdrive;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;
import io.camunda.connector.gdrive.model.GoogleDriveRequest;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveFunction implements ConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveFunction.class);

  private static final Gson GSON = new GsonBuilder().create();

  @Override
  public Object execute(ConnectorContext context) throws Exception {
    var requestAsJson = context.getVariables();
    final var connectorRequest = GSON.fromJson(requestAsJson, GoogleDriveRequest.class);

    var validator = new Validator();
    connectorRequest.validateWith(validator);
    validator.evaluate();

    connectorRequest.replaceSecrets(context.getSecretStore());

    return executeConnector(connectorRequest);
  }

  private GoogleDriveResult executeConnector(final GoogleDriveRequest connectorRequest) {
    // TODO: implement connector logic
    LOGGER.info("Executing my connector with request {}", connectorRequest);
    var result = new GoogleDriveResult();
    result.setMyProperty("NOT_IMPLEMENTED_YET");
    return result;
  }
}
