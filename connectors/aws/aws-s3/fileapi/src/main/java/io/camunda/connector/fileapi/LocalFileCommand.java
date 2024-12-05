package io.camunda.connector.fileapi;

import java.io.IOException;
import java.nio.file.Path;

public interface LocalFileCommand {

    Path saveFile(byte[] content, String filePath) throws IOException;

    byte[] loadFile(String filePath) throws IOException;

    void deleteFile(String filePath) throws IOException;

}
