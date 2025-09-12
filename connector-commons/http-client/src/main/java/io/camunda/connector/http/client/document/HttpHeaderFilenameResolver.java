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
package io.camunda.connector.http.client.document;

import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import java.util.Map;
import java.util.UUID;

public class HttpHeaderFilenameResolver {
  public static String getFilename(Map<String, Object> headers) {
    String filename = getFilenameFromContentDispositionHeader(headers);
    if (!filename.contains(".")) {
      filename += getFilenameFromContentType(headers);
    }
    return filename;
  }

  private static String getFilenameFromContentDispositionHeader(Map<String, Object> headers) {
    Object contentDispositionHeader =
        CustomApacheHttpClient.getHeaderIgnoreCase(headers, "Content-Disposition");
    if (contentDispositionHeader instanceof String contentDispositionHeaderString) {
      int index = contentDispositionHeaderString.indexOf("filename=");
      if (index != -1) {
        String filename = contentDispositionHeaderString.substring(index + 9).trim();
        if (filename.startsWith("\"") && filename.endsWith("\"")) {
          filename = filename.substring(1, filename.length() - 1);
        }
        return filename;
      }
    }
    return UUID.randomUUID().toString();
  }

  private static String getFilenameFromContentType(Map<String, Object> headers) {
    Object ctObj = CustomApacheHttpClient.getHeaderIgnoreCase(headers, "Content-Type");
    if (ctObj instanceof String ct && ct.contains("/")) {
      String subtype = ct.substring(ct.indexOf('/') + 1);
      return "." + subtype;
    }
    return "";
  }
}
