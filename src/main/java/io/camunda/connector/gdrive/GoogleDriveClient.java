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

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.IOException;
import java.util.List;

public class GoogleDriveClient {

  private static final String ERROR_CREATING = "An error occurred while creating the %s %s";

  private final Drive driveService;
  private final Docs docsService;

  public GoogleDriveClient(final Drive driveService, final Docs docsService) {
    this.driveService = driveService;
    this.docsService = docsService;
  }

  public File createWithMetadata(final File fileMetadata) {
    try {
      return driveService.files().create(fileMetadata).execute();
    } catch (IOException e) {
      throw new RuntimeException(
          String.format(ERROR_CREATING, "resource with name", fileMetadata.getName()), e);
    }
  }

  public File createWithTemplate(final File fileMetaData, final String templateId) {
    try {
      return driveService.files().copy(templateId, fileMetaData).execute();
    } catch (IOException e) {
      throw new RuntimeException(
          String.format(ERROR_CREATING, "file from template with id", templateId), e);
    }
  }

  public BatchUpdateDocumentResponse updateDocument(
      final String fileId, final List<Request> requests) {
    BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest().setRequests(requests);
    try {
      return docsService.documents().batchUpdate(fileId, body).execute();
    } catch (IOException e) {
      throw new RuntimeException("An error occurred while update file with id: " + fileId, e);
    }
  }
}
