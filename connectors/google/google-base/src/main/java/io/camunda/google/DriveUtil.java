/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google;

import static io.camunda.google.supplier.GoogleDriveServiceSupplier.createDriveClientInstance;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import io.camunda.google.model.Authentication;
import java.io.IOException;

public class DriveUtil {

  private DriveUtil() {}

  public static void moveFile(
      final Authentication auth, final String folderId, final String fileId) {
    Drive service = createDriveClientInstance(auth);

    try {
      String previousParents = getPreviousParents(service, fileId);
      service
          .files()
          .update(fileId, null)
          .setAddParents(folderId)
          .setRemoveParents(previousParents)
          .setFields("parents")
          .execute();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getPreviousParents(Drive service, String fileId) throws IOException {

    File file = service.files().get(fileId).setFields("parents").execute();
    StringBuilder previousParents = new StringBuilder();
    for (String parent : file.getParents()) {
      previousParents.append(parent);
      previousParents.append(',');
    }

    return previousParents.toString();
  }
}
