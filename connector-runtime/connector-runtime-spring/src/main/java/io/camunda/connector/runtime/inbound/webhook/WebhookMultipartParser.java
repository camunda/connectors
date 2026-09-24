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
package io.camunda.connector.runtime.inbound.webhook;

import io.camunda.connector.api.inbound.webhook.Part;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.fileupload2.core.AbstractFileUpload;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadFileCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.core.RequestContext;

final class WebhookMultipartParser {

  private WebhookMultipartParser() {}

  static List<Part> parse(
      byte[] rawBody,
      String contentType,
      String characterEncoding,
      long maxRequestSize,
      long maxFileSize,
      long maxPartCount) {
    var upload = new BufferedFileUpload();
    upload.setMaxSize(maxRequestSize);
    upload.setMaxFileSize(maxFileSize);
    upload.setMaxFileCount(maxPartCount);

    try {
      if (maxPartCount >= 0 && (maxRequestSize < 0 || rawBody.length <= maxRequestSize)) {
        enforcePartCount(rawBody, contentType, maxPartCount, upload);
      }
      var items =
          upload.getItemIterator(
              new BufferedRequestContext(rawBody, contentType, characterEncoding));
      var parts = new ArrayList<Part>();
      while (items.hasNext()) {
        if (maxPartCount >= 0 && parts.size() >= maxPartCount) {
          throw new MultipartSizeExceededException(
              new FileUploadFileCountLimitException(
                  "Multipart part count exceeds the configured limit",
                  parts.size() + 1,
                  maxPartCount));
        }
        var item = items.next();
        try (var inputStream = item.getInputStream()) {
          parts.add(
              new Part(
                  item.getFieldName(),
                  item.isFormField() ? null : item.getName(),
                  new ByteArrayInputStream(inputStream.readAllBytes()),
                  item.getContentType()));
        }
      }
      return parts;
    } catch (FileUploadSizeException e) {
      throw new MultipartSizeExceededException(e);
    } catch (FileUploadException e) {
      throw new MalformedMultipartException(e);
    } catch (IllegalStateException e) {
      throw new MalformedMultipartException(e);
    } catch (InvalidPathException e) {
      throw new MalformedMultipartException(e);
    } catch (IOException e) {
      throw new MultipartReadException(e);
    }
  }

  private static void enforcePartCount(
      byte[] rawBody, String contentType, long maxPartCount, BufferedFileUpload upload) {
    var boundary = upload.getBoundary(contentType);
    if (boundary == null) {
      return;
    }
    long partCount = 0;
    int delimiterLength = boundary.length + 2;
    for (int offset = 0; offset + delimiterLength + 1 < rawBody.length; offset++) {
      boolean startsLine =
          offset == 0
              || (offset >= 2 && rawBody[offset - 2] == '\r' && rawBody[offset - 1] == '\n');
      if (!startsLine
          || rawBody[offset] != '-'
          || rawBody[offset + 1] != '-'
          || !matches(rawBody, offset + 2, boundary)) {
        continue;
      }
      int suffixOffset = offset + delimiterLength;
      boolean startsPart = rawBody[suffixOffset] == '\r' && rawBody[suffixOffset + 1] == '\n';
      if (startsPart && ++partCount > maxPartCount) {
        throw new MultipartSizeExceededException(
            new FileUploadFileCountLimitException(
                "Multipart part count exceeds the configured limit", partCount, maxPartCount));
      }
    }
  }

  private static boolean matches(byte[] body, int offset, byte[] expected) {
    if (offset + expected.length > body.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if (body[offset + i] != expected[i]) {
        return false;
      }
    }
    return true;
  }

  static final class MalformedMultipartException extends RuntimeException {
    MalformedMultipartException(Throwable cause) {
      super(cause);
    }
  }

  static final class MultipartReadException extends RuntimeException {
    MultipartReadException(Throwable cause) {
      super(cause);
    }
  }

  static final class MultipartSizeExceededException extends RuntimeException {
    MultipartSizeExceededException(Throwable cause) {
      super(cause);
    }
  }

  private static final class BufferedFileUpload
      extends AbstractFileUpload<BufferedRequestContext, DiskFileItem, DiskFileItemFactory> {

    @Override
    public FileItemInputIterator getItemIterator(BufferedRequestContext request)
        throws FileUploadException, IOException {
      return super.getItemIterator((RequestContext) request);
    }

    @Override
    public Map<String, List<DiskFileItem>> parseParameterMap(BufferedRequestContext request)
        throws FileUploadException {
      return super.parseParameterMap((RequestContext) request);
    }

    @Override
    public List<DiskFileItem> parseRequest(BufferedRequestContext request)
        throws FileUploadException {
      return super.parseRequest((RequestContext) request);
    }
  }

  private record BufferedRequestContext(
      byte[] rawBody, String contentType, String characterEncoding) implements RequestContext {

    @Override
    public String getCharacterEncoding() {
      return characterEncoding;
    }

    @Override
    public long getContentLength() {
      return rawBody.length;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public ByteArrayInputStream getInputStream() {
      return new ByteArrayInputStream(rawBody);
    }

    @Override
    public boolean isMultipartRelated() {
      return false;
    }
  }
}
