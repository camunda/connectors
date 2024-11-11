package io.camunda.connector.fileapi;
import io.camunda.connector.fileapi.model.FileContent;
import io.camunda.connector.fileapi.model.RequestData;

import java.io.IOException;
import java.util.Objects;

public class ProcessFileService implements ProcessFileCommand {

    private final RemoteFileCommand remoteFileCommand;
    private final LocalFileCommand localFileCommand;

    public ProcessFileService(RemoteFileCommand remoteFileCommand, LocalFileCommand localFileCommand) {
        this.remoteFileCommand = remoteFileCommand;
        this.localFileCommand = localFileCommand;
    }

    public RequestData uploadFile(RequestData request) throws IOException {
        String contentType = Objects.requireNonNull(request.getContentType(), "Content type must be set");
        byte[] content = localFileCommand.loadFile(request.getFilePath());
        remoteFileCommand.putFile(request, FileContent.builder()
                .content(content)
                .contentLength((long) content.length)
                .contentType(contentType)
                .build()
        );
        return request;
    }

    public RequestData deleteFile(RequestData request) throws IOException {
        localFileCommand.deleteFile(request.getFilePath());
        remoteFileCommand.deleteFile(request);
        return request;
    }

    public RequestData downloadFile(RequestData request) throws IOException {
        FileContent response = remoteFileCommand.getFile(request);
        localFileCommand.saveFile(response.getContent(), request.getFilePath());
        // overwrite with actual content type
        request.setContentType(request.getContentType());
        return request;
    }
}
