/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.util;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

public final class FileUtil {

  private FileUtil() {}

  public static Pair<String, String> defineNameAndType(String fileName) {
    String separator = ".";
    int separatorIndex = fileName.lastIndexOf(separator);

    if (separatorIndex == -1) {
      return Pair.of(fileName, "");
    }

    String name = fileName.substring(0, separatorIndex);
    String type = fileName.substring(separatorIndex + 1);
    return Pair.of(name, type);
  }

  public static String defineType(String contentType) throws MimeTypeException {
    MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
    String extension = mimeTypes.forName(contentType).getExtension();

    int dotIndex = extension.indexOf('.');
    return dotIndex == 0 ? extension.substring(1) : extension;
  }
}
