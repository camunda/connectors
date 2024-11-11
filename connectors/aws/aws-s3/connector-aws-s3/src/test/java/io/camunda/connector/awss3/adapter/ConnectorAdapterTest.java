package io.camunda.connector.awss3.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.awss3.in.ConnectorAdapter;
import io.camunda.connector.awss3.in.model.*;
import io.camunda.connector.awss3.out.cloud.CloudClientFactory;
import io.camunda.connector.awss3.out.cloud.CloudFileAdapter;
import io.camunda.connector.awss3.out.local.LocalFileAdapter;
import io.camunda.connector.fileapi.ProcessFileCommand;
import io.camunda.connector.fileapi.ProcessFileService;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorAdapterTest {

    @Mock
    CloudClientFactory factory; // mock component to S3

    @Mock
    LocalFileAdapter localFileAdapter; // mock component to local file system

    private ConnectorAdapter connector;

    @BeforeEach
    public void setup() {
        ProcessFileCommand processFileCommand = new ProcessFileService(new CloudFileAdapter(factory), localFileAdapter);
        connector = new ConnectorAdapter(processFileCommand);
    }

    @Test
    void happy_path_aws_put_is_called_as_expected() throws IOException {
        // given
        S3Client client = Mockito.mock(S3Client.class);
        when(factory.createClient(any(), any(), any())).thenReturn(client);
        PutObjectResponse awsResult = createPutResponse();
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(awsResult);

        byte[] fileBytes = "hello, world!".getBytes(StandardCharsets.UTF_8);
        when(localFileAdapter.loadFile(anyString())).thenReturn(fileBytes);

        ConnectorRequest request = new ConnectorRequest();
        request.setAuthentication(getAuthentication());
        String filePath = "my/path/to/file.txt";
        request.setRequestDetails(getPutDetails("bucket", "path/file.txt", filePath));

        ConnectorResponse expectedResponse = new ConnectorResponse();
        expectedResponse.setFilePath(filePath);
        expectedResponse.setObjectKey("path/file.txt");
        expectedResponse.setBucketName("bucket");
        expectedResponse.setContentType("application/text");

        OutboundConnectorContextBuilder.TestConnectorContext context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // when
        ConnectorResponse actualResult = (ConnectorResponse) connector.execute(context);

        // then
        assertThat(actualResult).isEqualTo(expectedResponse);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(client, times(1)).putObject(requestCaptor.capture(), bodyCaptor.capture());
        PutObjectRequest putRequest = requestCaptor.getValue();
        assertThat(putRequest.bucket()).isEqualTo("bucket");
        assertThat(putRequest.key()).isEqualTo("path/file.txt");
        assertThat(putRequest.contentLength()).isEqualTo(fileBytes.length);
        assertThat(putRequest.contentType()).isEqualTo("application/text");

        RequestBody requestBody = bodyCaptor.getValue();
        try (InputStream is = requestBody.contentStreamProvider().newStream()) {
            assertThat(is.readAllBytes()).isEqualTo(fileBytes);
        }

        verify(localFileAdapter, times(1)).loadFile(eq(filePath));
        verify(client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void happy_path_aws_delete_is_called_as_expected() throws IOException {
        // given
        S3Client client = Mockito.mock(S3Client.class);
        when(factory.createClient(any(), any(), any())).thenReturn(client);

        ConnectorRequest request = new ConnectorRequest();
        request.setAuthentication(getAuthentication());
        request.setRequestDetails(getDeleteDetails("bucket", "path/file.txt", "my/path/file.txt"));

        OutboundConnectorContextBuilder.TestConnectorContext context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // when
        ConnectorResponse actualResult = (ConnectorResponse) connector.execute(context);

        // then
        ConnectorResponse response = new ConnectorResponse();
        response.setObjectKey("path/file.txt");
        response.setBucketName("bucket");
        response.setFilePath("my/path/file.txt");
        assertThat(actualResult).isEqualTo(response);

        ArgumentCaptor<DeleteObjectRequest> argumentCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client, times(1)).deleteObject(argumentCaptor.capture());
        DeleteObjectRequest deleteRequest = argumentCaptor.getValue();
        assertThat(deleteRequest.bucket()).isEqualTo("bucket");
        assertThat(deleteRequest.key()).isEqualTo("path/file.txt");

        verify(localFileAdapter, times(1)).deleteFile(eq("my/path/file.txt"));
        verify(client, times(1)).deleteObject(any(DeleteObjectRequest.class));

    }

    @Test
    void happy_path_aws_get_is_called_as_expected() throws IOException {
        // given
        S3Client client = Mockito.mock(S3Client.class);
        when(factory.createClient(any(), any(), any())).thenReturn(client);

        String filePath = "my/path/to/file.txt";
        byte[] fileBytes = "hello, world!".getBytes(StandardCharsets.UTF_8);
        when(localFileAdapter.saveFile(eq(fileBytes), eq(filePath))).thenReturn(Path.of(filePath));

        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength(1L)
                .contentType("application/text")
                .build();
        ResponseInputStream<GetObjectResponse> result = new ResponseInputStream<>(response, new ByteArrayInputStream(fileBytes));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(result);

        ConnectorRequest request = new ConnectorRequest();
        request.setAuthentication(getAuthentication());
        request.setRequestDetails(getGetDetails("bucket", "path/file.txt", filePath));

        ConnectorResponse expectedResponse = new ConnectorResponse();
        expectedResponse.setFilePath(filePath);
        expectedResponse.setObjectKey("path/file.txt");
        expectedResponse.setBucketName("bucket");
        expectedResponse.setContentType("application/text");

        OutboundConnectorContextBuilder.TestConnectorContext context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // when
        ConnectorResponse actualResult = (ConnectorResponse) connector.execute(context);

        // then
        assertThat(actualResult).isEqualTo(expectedResponse);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client, times(1)).getObject(requestCaptor.capture());
        GetObjectRequest getRequest = requestCaptor.getValue();
        assertThat(getRequest.bucket()).isEqualTo("bucket");
        assertThat(getRequest.key()).isEqualTo("path/file.txt");

        verify(localFileAdapter, times(1)).saveFile(eq(fileBytes), eq(filePath));
        verify(client, times(1)).getObject(any(GetObjectRequest.class));
    }


    private static PutObjectResponse createPutResponse() {
        return PutObjectResponse.builder()
                .versionId("1234567")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .checksumSHA256("foo")
                .build();
    }

    private RequestDetails getPutDetails(String bucket, String key, String path) {
        RequestDetails details = new RequestDetails();
        details.setBucketName(bucket);
        details.setObjectKey(key);
        details.setContentType("application/text");
        details.setFilePath(path);
        details.setOperationType(OperationType.PUT_OBJECT);
        details.setRegion("eu-central-1");
        return details;
    }

    private RequestDetails getGetDetails(String bucket, String key, String path) {
        RequestDetails details = new RequestDetails();
        details.setBucketName(bucket);
        details.setObjectKey(key);
        details.setContentType("application/text");
        details.setFilePath(path);
        details.setOperationType(OperationType.GET_OBJECT);
        details.setRegion("eu-central-1");
        return details;
    }

    private RequestDetails getDeleteDetails(String bucket, String key, String path) {
        RequestDetails details = new RequestDetails();
        details.setBucketName(bucket);
        details.setObjectKey(key);
        details.setFilePath(path);
        details.setOperationType(OperationType.DELETE_OBJECT);
        details.setRegion("eu-central-1");
        return details;
    }

    private AuthenticationRequestData getAuthentication() {
        AuthenticationRequestData authentication = new AuthenticationRequestData();
        authentication.setAccessKey("secrets.AWS_ACCESS_KEY");
        authentication.setSecretKey("secrets.AWS_SECRET_KEY");
        return authentication;
    }
}