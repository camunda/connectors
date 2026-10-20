package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.junit.jupiter.api.Test;

class ScopedFeedbackTest {
  @Test void feedbackIsReadFromExactOwnedFirstTaskScope() throws Exception {
    var server = new com.github.tomakehurst.wiremock.WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    try {
      var api = new Api(server.baseUrl(), false);
      String body = Demo.JSON.writeValueAsString(java.util.Map.of(
          "page", java.util.Map.of("totalItems", 1), "items", java.util.List.of(java.util.Map.of(
              "processInstanceKey", "123", "scopeKey", "first-review", "name", "analystFeedback",
              "value", Demo.JSON.writeValueAsString(Api.ANALYST_FEEDBACK)))));
      server.stubFor(post(urlEqualTo("/v2/variables/search")).willReturn(okJson(body)));
      assertThat(api.scopedVariable(123, "first-review", "analystFeedback").asText())
          .isEqualTo(Api.ANALYST_FEEDBACK);
      server.verify(postRequestedFor(urlEqualTo("/v2/variables/search"))
          .withRequestBody(matchingJsonPath("$.filter.scopeKey", equalTo("first-review")))
          .withRequestBody(matchingJsonPath("$.filter.processInstanceKey", equalTo("123"))));
      server.stubFor(post(urlEqualTo("/v2/variables/search"))
          .willReturn(okJson(body.replace("first-review", "second-review"))));
      assertThatThrownBy(() -> api.scopedVariable(123, "first-review", "analystFeedback"))
          .isInstanceOf(AssertionError.class).hasMessageContaining("owned process");
      server.stubFor(post(urlEqualTo("/v2/variables/search"))
          .willReturn(okJson("{\"page\":{\"totalItems\":0},\"items\":[]}")));
      assertThatThrownBy(() -> api.scopedVariable(123, "first-review", "analystFeedback"))
          .isInstanceOf(AssertionError.class).hasMessageContaining("exactly one");
    } finally {
      server.stop();
    }
  }
}
