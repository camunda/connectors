package io.camunda.connector.email.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.client.jakarta.inbound.JakartaEmailListener;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class InboundEmailTest extends BaseEmailTest {

  JakartaEmailListener jakartaEmailListener = new JakartaEmailListener();

  private static List<EmailInboundConnectorProperties> createEmailInboundConnectorProperties() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    try {
      return objectMapper
          .readValue(
              Files.readString(
                  Path.of("src/test/resources/integration/inbound-connector-happy-path.json")),
                  new TypeReference<List<EmailInboundConnectorProperties>>(){});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest
  @MethodSource("createEmailInboundConnectorProperties")
  public void shouldReceiveEmailAndSetAsSeen(EmailInboundConnectorProperties emailInboundConnectorProperties)  {
    InboundConnectorContext inboundConnectorContext = mock(InboundConnectorContext.class);

    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class)).thenReturn(emailInboundConnectorProperties);

    jakartaEmailListener.startListener(inboundConnectorContext);

    verify(inboundConnectorContext.canActivate(any()), times(1));
  }
}
