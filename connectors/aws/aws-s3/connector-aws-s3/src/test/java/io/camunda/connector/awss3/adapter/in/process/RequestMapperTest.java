package io.camunda.connector.awss3.adapter.in.process;

import io.camunda.connector.fileapi.model.RequestData;
import io.camunda.connector.awss3.in.RequestMapper;
import io.camunda.connector.awss3.in.model.AuthenticationRequestData;
import io.camunda.connector.awss3.in.model.OperationType;
import io.camunda.connector.awss3.in.model.RequestDetails;
import io.camunda.connector.awss3.in.model.ConnectorRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestMapperTest {

    @Test
    void mapping_is_as_expected() {
        // given
        ConnectorRequest request = new ConnectorRequest();
        request.setRequestDetails(getDetails());
        request.setAuthentication(getAuthentication());

        // when
        RequestData requestData = RequestMapper.mapRequest(request);

        // then
        assertThat(requestData.getRegion())
                .as("region")
                .isEqualTo(request.getRequestDetails().getRegion());
        assertThat(requestData.getBucket())
                .as("bucket")
                .isEqualTo(request.getRequestDetails().getBucketName());
        assertThat(requestData.getKey())
                .as("key")
                .isEqualTo(request.getRequestDetails().getObjectKey());
        assertThat(requestData.getFilePath())
                .as("file path")
                .isEqualTo(request.getRequestDetails().getFilePath());
        assertThat(requestData.getContentType())
                .as("content type")
                .isEqualTo(request.getRequestDetails().getContentType());
        assertThat(requestData.getAuthenticationKey())
                .as("access key")
                .isEqualTo(request.getAuthentication().getAccessKey());
        assertThat(requestData.getAuthenticationSecret())
                .as("secret key")
                .isEqualTo(request.getAuthentication().getSecretKey());
    }

    @Test
    void mapping_is_as_expected_with_key_as_fallback_for_file_path() {
        // given
        ConnectorRequest request = new ConnectorRequest();
        request.setRequestDetails(getDetails());
        request.getRequestDetails().setFilePath(null);
        request.setAuthentication(getAuthentication());

        // when
        RequestData requestData = RequestMapper.mapRequest(request);

        // then
        assertThat(requestData.getFilePath())
                .as("file path")
                .isEqualTo(request.getRequestDetails().getObjectKey());
    }

    private RequestDetails getDetails() {
        RequestDetails details = new RequestDetails();
        details.setBucketName("bucket");
        details.setContentType("application/text");
        details.setFilePath("/tmp/invoice.txt");
        details.setObjectKey("/invoice/invoice-123.txt");
        details.setOperationType(OperationType.PUT_OBJECT);
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