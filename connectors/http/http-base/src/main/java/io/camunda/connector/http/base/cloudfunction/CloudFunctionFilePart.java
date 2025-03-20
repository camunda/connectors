package io.camunda.connector.http.base.cloudfunction;

/**
 * Represents a part of a multipart form submission. Can contain either files or a fields. This
 * record prevents any dependency on the Servlet API. This part is used only in the {@link
 * io.camunda.connector.http.base.ExecutionEnvironment.SaaSCloudFunction} environment.
 *
 * @param name The name of the field in the multipart form corresponding to this part.
 * @param submittedFileName If this part represents an uploaded file, gets the file name submitted
 *     in the upload. Returns {@code null} if no file name is available or if this part is not a
 *     file upload.
 * @param content The content of this part.
 * @param contentType The content type of this part.
 */
public record CloudFunctionFilePart(
    String name, String submittedFileName, byte[] content, String contentType) {}
