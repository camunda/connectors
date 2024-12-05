import io.camunda.connector.fileapi.LocalFileCommand;
import io.camunda.connector.fileapi.ProcessFileService;
import io.camunda.connector.fileapi.RemoteFileCommand;
import io.camunda.connector.fileapi.model.FileContent;
import io.camunda.connector.fileapi.model.RequestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProcessFileServiceTest {

    @Mock
    RemoteFileCommand remoteFileCommand;

    @Mock
    LocalFileCommand localFileCommand;

    private ProcessFileService service;

    @BeforeEach
    void setUp() {
        service = new ProcessFileService(remoteFileCommand, localFileCommand);
    }

    @Test
    void file_service_for_upload_called_as_expected() throws IOException {
        // given
        RequestData requestData = RequestData.builder()
                .filePath("myfile.txt")
                .contentType("contentType")
                .build();
        byte[] expectedContent = "foo".getBytes(StandardCharsets.UTF_8);
        when(localFileCommand.loadFile(anyString())).thenReturn(expectedContent);

        // when
        RequestData response = service.uploadFile(requestData);

        // then
        verify(localFileCommand, times(1)).loadFile(eq("myfile.txt"));
        ArgumentCaptor<FileContent> argumentCaptor = ArgumentCaptor.forClass(FileContent.class);
        verify(remoteFileCommand, times(1)).putFile(eq(requestData), argumentCaptor.capture());
        FileContent value = argumentCaptor.getValue();
        assertThat(value.getContent()).as("content").isEqualTo(expectedContent);
        assertThat(value.getContentLength()).as("content length").isEqualTo(expectedContent.length);
        assertThat(value.getContentType()).as("content type").isEqualTo("contentType");
        assertThat(response).isEqualTo(requestData);
    }

    @Test
    void file_service_for_delete_called_as_expected() throws IOException {
        // given
        RequestData requestData = RequestData.builder()
                .filePath("myfile.txt")
                .build();

        // when
        RequestData response = service.deleteFile(requestData);

        // then
        verify(localFileCommand, times(1)).deleteFile(eq("myfile.txt"));
        verify(remoteFileCommand, times(1)).deleteFile(eq(requestData));
        assertThat(response).isEqualTo(requestData);
    }

    @Test
    void file_service_for_download_called_as_expected() throws IOException {
        // given
        RequestData requestData = RequestData.builder()
                .filePath("myfile.txt")
                .contentType("contentType")
                .build();
        byte[] expectedContent = "foo".getBytes(StandardCharsets.UTF_8);
        when(remoteFileCommand.getFile(eq(requestData))).thenReturn(FileContent.builder().content(expectedContent).build());
        when(localFileCommand.saveFile(eq(expectedContent), eq("myfile.txt"))).thenReturn(Path.of("myfile.txt"));

        // when
        RequestData response = service.downloadFile(requestData);

        // then
        verify(remoteFileCommand, times(1)).getFile(eq(requestData));
        verify(localFileCommand, times(1)).saveFile(eq(expectedContent), eq("myfile.txt"));
        assertThat(response).isEqualTo(requestData);
    }
}