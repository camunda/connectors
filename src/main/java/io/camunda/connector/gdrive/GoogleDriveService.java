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

import com.google.api.services.drive.model.File;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveService.class);

  private final GoogleDriveClient client;

  public GoogleDriveService() {
    this(new GoogleDriveClient());
  }

  public GoogleDriveService(final GoogleDriveClient client) {
    this.client = client;
  }

  public GoogleDriveResult execute(final GoogleDriveRequest request) {
    client.init(request.getAuthentication());
    File metaData = client.createMetaData(request.getFolder());
    File folder = client.createFolder(metaData);
    client.shutdown();

    LOGGER.debug(
        "Resource was created on google drive with id [{}] and name [{}]",
        folder.getId(),
        request.getFolder().getName());
    return new GoogleDriveResult(folder.getId());
  }
}
