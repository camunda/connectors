package io.camunda.connector.api.inbound.webhook;

import java.io.InputStream;

/**
 * Represents a part of a multipart form submission. Can contain either files or a fields. This
 * record prevents any dependency on the Servlet API.
 *
 * @param name The name of the field in the multipart form corresponding to this part.
 * @param submittedFileName If this part represents an uploaded file, gets the file name submitted
 *     in the upload. Returns {@code null} if no file name is available or if this part is not a
 *     file upload.
 * @param inputStream Obtain an InputStream that can be used to retrieve the contents of the file.
 * @param contentType The content type of this part.
 */
public record Part(
    String name, String submittedFileName, InputStream inputStream, String contentType) {}
