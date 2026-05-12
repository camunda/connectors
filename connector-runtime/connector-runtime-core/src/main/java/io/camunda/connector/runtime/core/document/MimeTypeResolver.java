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
package io.camunda.connector.runtime.core.document;

import java.net.URLConnection;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves MIME content types from filenames using the JDK's built-in {@link
 * URLConnection#guessContentTypeFromName(String)}.
 *
 * <p>Returns {@code application/octet-stream} when no type can be inferred — including when the
 * filename has no extension, has an unrecognized extension, or {@code guessContentTypeFromName}
 * otherwise returns {@code null}.
 */
public final class MimeTypeResolver {

  private static final Logger LOG = LoggerFactory.getLogger(MimeTypeResolver.class);
  static final String OCTET_STREAM = "application/octet-stream";

  private MimeTypeResolver() {}

  public static String resolveContentType(String explicitContentType, String fileName) {
    if (StringUtils.isNotBlank(explicitContentType)) {
      LOG.debug("Using explicit content type: {}", explicitContentType);
      return explicitContentType;
    }
    if (StringUtils.isNotBlank(fileName)) {
      String guessed = URLConnection.guessContentTypeFromName(fileName);
      if (guessed != null) {
        LOG.debug("Resolved content type '{}' from file name '{}'", guessed, fileName);
        return guessed;
      }
    }
    LOG.debug("Could not resolve content type, falling back to '{}'", OCTET_STREAM);
    return OCTET_STREAM;
  }
}
