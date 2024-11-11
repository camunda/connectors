package io.camunda.connector.fileapi;

import java.io.IOException;

import io.camunda.connector.fileapi.model.FileContent;
import io.camunda.connector.fileapi.model.RequestData;

public interface RemoteFileCommand {
    void deleteFile(RequestData requestData);
    void putFile(RequestData requestData, FileContent fileContent) throws IOException;
    FileContent getFile(RequestData requestData) throws IOException;
}
