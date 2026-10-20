package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.junit.jupiter.api.Test;

class ResourceConsistencyTest {
  @Test void deploymentKeyReadWaitsOnlyForNotFoundAndChecksExactBytes() throws Exception {
    var server = new com.github.tomakehurst.wiremock.WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    try {
      server.stubFor(get(urlEqualTo("/v2/resources/owned-key/content/binary"))
          .inScenario("indexing").whenScenarioStateIs("Started").willSetStateTo("indexed")
          .willReturn(aResponse().withStatus(404)));
      server.stubFor(get(urlEqualTo("/v2/resources/owned-key/content/binary"))
          .inScenario("indexing").whenScenarioStateIs("indexed").willReturn(ok().withBody("approved")));
      assertThat(new Api(server.baseUrl(), false).deployedResource("owned-key", "approved".getBytes()))
          .isEqualTo("approved".getBytes());
      server.stubFor(get(urlEqualTo("/v2/resources/forbidden/content/binary"))
          .willReturn(aResponse().withStatus(403)));
      assertThatThrownBy(() -> new Api(server.baseUrl(), false).deployedResource("forbidden", new byte[0]))
          .isInstanceOf(java.io.IOException.class).hasMessageContaining("403");
    } finally {
      server.stop();
    }
  }
}
