package io.camunda.connector.awss3.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.awss3.in.model.AuthenticationRequestData;
import io.camunda.connector.awss3.in.model.ConnectorRequest;
import io.camunda.connector.awss3.in.model.OperationType;
import io.camunda.connector.awss3.in.model.RequestDetails;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder.TestConnectorContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorAdapterContextTest {

    @Test
    void should_replace_secrets() throws JsonProcessingException {
        // given
        ConnectorRequest request = new ConnectorRequest();
        request.setAuthentication(getAuthentication());
        request.setRequestDetails(getDetails());

        TestConnectorContext context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // when
        ConnectorRequest details = context.bindVariables(ConnectorRequest.class);

        // then
        assertThat(details).extracting("authentication")
                .extracting("accessKey")
                .as("access key")
                .isEqualTo("abc");

        assertThat(details).extracting("authentication")
                .extracting("secretKey")
                .as("secret key")
                .isEqualTo("123");
    }

    @Test
    void should_fail_if_authentication_is_missing() throws JsonProcessingException {
        // setup
        ConnectorRequest request = new ConnectorRequest();
        request.setRequestDetails(getDetails());
        TestConnectorContext context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // expect
        assertThatThrownBy(() -> context.validate(request))
                .isInstanceOf(ConnectorInputException.class)
                .hasMessage("jakarta.validation.ValidationException: Found constraints violated while validating input: \n - Property: authentication: Validation failed.");
    }

    @Test
    void should_fail_if_details_are_missing() throws JsonProcessingException {
        // setup
        ConnectorRequest request = new ConnectorRequest();
        AuthenticationRequestData authentication = getAuthentication();
        request.setAuthentication(authentication);
        TestConnectorContext context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // expect
        assertThatThrownBy(() -> context.validate(request))
                .isInstanceOf(ConnectorInputException.class)
                .hasMessage("jakarta.validation.ValidationException: Found constraints violated while validating input: \n - Property: requestDetails: Validation failed.");
    }

    @Test
    void should_fail_if_required_details_values_are_missing() throws JsonProcessingException {
        // setup
        ConnectorRequest request = new ConnectorRequest();
        request.setAuthentication(getAuthentication());
        request.setRequestDetails(new RequestDetails());
        var context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // expect
        assertThatThrownBy(() -> context.validate(request))
                .isInstanceOf(ConnectorInputException.class)
                .hasMessageContainingAll(
                        "requestDetails.bucketName: Validation failed",
                        "requestDetails.region: Validation failed",
                        "requestDetails.objectKey: Validation failed",
                        "requestDetails.operationType: Validation failed"
                )
                .hasMessageNotContainingAny(
                        "requestDetails.contentType: Validation failed",
                        "requestDetails.filePath: Validation failed"
                );

    }

    @Test
    void should_fail_if_required_authentication_value_are_missing() throws JsonProcessingException {
        // setup
        ConnectorRequest request = new ConnectorRequest();
        request.setAuthentication(new AuthenticationRequestData());
        request.setRequestDetails(getDetails());
        var context = OutboundConnectorContextBuilder.create()
                .secret("AWS_ACCESS_KEY", "abc")
                .secret("AWS_SECRET_KEY", "123")
                .variables(new ObjectMapper().writeValueAsString(request))
                .build();

        // expect
        assertThatThrownBy(() -> context.validate(request))
                .isInstanceOf(ConnectorInputException.class)
                .hasMessageContainingAll("authentication.secretKey", "authentication.accessKey");

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