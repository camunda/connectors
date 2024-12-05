package io.camunda.connector.fileapi;

import io.camunda.connector.fileapi.model.RequestData;

import java.io.IOException;

public interface ProcessFileCommand {

    RequestData uploadFile(RequestData request) throws IOException;

    RequestData deleteFile(RequestData request) throws IOException;

    RequestData downloadFile(RequestData request) throws IOException;

}
