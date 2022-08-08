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

import com.google.api.client.json.JsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.gdrive.supliers.GJsonComponentSupplier;
import io.camunda.connector.gdrive.supliers.GoogleDriveSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveFunction implements ConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveFunction.class);

  private final GoogleDriveService service;
  private final Gson gson;
  private final JsonFactory jsonFactory;

  public GoogleDriveFunction() {
    this(
        new GoogleDriveService(GJsonComponentSupplier.getGson()),
        GJsonComponentSupplier.getGson(),
        GJsonComponentSupplier.getJsonFactory());
  }

  public GoogleDriveFunction(
      final GoogleDriveService service, final Gson gson, final JsonFactory jsonFactory) {
    this.service = service;
    this.gson = gson;
    this.jsonFactory = jsonFactory;
  }

  @Override
  public Object execute(final ConnectorContext context) {
    var requestAsJson = context.getVariables();
    final var request = gson.fromJson(requestAsJson, GoogleDriveRequest.class);
    context.validate(request);
    context.replaceSecrets(request);
    LOGGER.debug("Request verified successfully and all required secrets replaced");
    return executeConnector(request);
  }

  private GoogleDriveResult executeConnector(final GoogleDriveRequest request) {
    LOGGER.debug("Executing my connector with request {}", request);
    GoogleDriveClient drive = getDriveClient(request.getToken());
    return service.execute(drive, request.getResource());
  }

  private GoogleDriveClient getDriveClient(final String token) {
    return new GoogleDriveClient(GoogleDriveSupplier.createDriveClientInstance(token, jsonFactory));
  }
}
