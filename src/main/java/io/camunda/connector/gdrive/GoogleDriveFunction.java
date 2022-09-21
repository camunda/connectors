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

import com.google.api.client.json.JsonParser;
import com.google.api.client.json.gson.GsonFactory;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.gdrive.supliers.GoogleServicesSupplier;
import io.camunda.connector.gdrive.supliers.GsonComponentSupplier;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveFunction.class);

  private final GoogleDriveService service;
  private final GsonFactory gsonFactory;

  public GoogleDriveFunction() {
    this(new GoogleDriveService(), GsonComponentSupplier.gsonFactoryInstance());
  }

  public GoogleDriveFunction(final GoogleDriveService service, final GsonFactory gsonFactory) {
    this.service = service;
    this.gsonFactory = gsonFactory;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    final GoogleDriveRequest request = parseVariablesToRequest(context.getVariables());
    context.validate(request);
    context.replaceSecrets(request);
    LOGGER.debug("Request verified successfully and all required secrets replaced");
    return executeConnector(request);
  }

  private GoogleDriveRequest parseVariablesToRequest(final String requestAsJson) {
    try {
      JsonParser jsonParser = gsonFactory.createJsonParser(requestAsJson);
      return jsonParser.parseAndClose(GoogleDriveRequest.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private GoogleDriveResult executeConnector(final GoogleDriveRequest request) {
    LOGGER.debug("Executing my connector with request {}", request);
    GoogleDriveClient drive =
        new GoogleDriveClient(
            GoogleServicesSupplier.createDriveClientInstance(request.getAuthentication()),
            GoogleServicesSupplier.createDocsClientInstance(request.getAuthentication()));
    return service.execute(drive, request.getResource());
  }
}
