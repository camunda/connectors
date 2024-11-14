package io.camunda.connector.runtime.inbound.webhook;

import static org.mockito.Mockito.*;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

public class InboundWebhookRestControllerTest {

  //  private static InboundConnectorContextImpl context = mock(InboundConnectorContextImpl.class);
  //  private static WebhookConnectorRegistry webhookConnectorRegistry =
  //      mock(WebhookConnectorRegistry.class);
  //  private InboundWebhookRestController controller =
  //      new InboundWebhookRestController(webhookConnectorRegistry);
  //
  //  @BeforeAll
  //  public static void setup() {
  //    when(webhookConnectorRegistry.getWebhookConnectorByContextPath(anyString()))
  //        .thenReturn(
  //            Optional.of(
  //                new RegisteredExecutable.Activated(
  //                    (WebhookConnectorExecutable)
  //                        payload ->
  //                            new WebhookResult() {
  //                              @Override
  //                              public MappedHttpRequest request() {
  //                                return mock(MappedHttpRequest.class);
  //                              }
  //                            },
  //                    mock(InboundConnectorContextImpl.class))));
  //  }
  //
  //  @Test
  //  void greetingShouldReturnMessageFromService() throws Exception {
  //    Path filePath = Paths.get("src/test/resources/files/text.txt");
  //    byte[] fileContent = Files.readAllBytes(filePath);
  //
  //    // Create a MockMultipartFile
  //    MockMultipartFile mockMultipartFile =
  //        new MockMultipartFile("file1", "text.txt", "text/plain", fileContent);
  //
  //    // Create a MockMultipartHttpServletRequest
  //    MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
  //    request.addFile(mockMultipartFile);
  //    request.setParameter("param1", "value1");
  //    request.addHeader("header1", "value1");
  //
  //    controller.inbound("test", Map.of(), null, null, request);
  //  }
}
