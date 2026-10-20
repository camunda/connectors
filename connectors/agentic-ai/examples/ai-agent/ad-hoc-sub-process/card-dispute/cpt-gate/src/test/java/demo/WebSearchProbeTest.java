package demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;

class WebSearchProbeTest {
  @Test void onlyExactRegionalRejectionIsEligible() {
    assertThat(WebSearchProbe.classify("HTTP 403: not available on region-specific endpoints"))
        .isEqualTo("REGIONAL_UNAVAILABLE");
    for (var error : new String[] {"HTTP 403: invalid credential", "HTTP 429", "", "tools unsupported", "HTTP 500"}) {
      assertThat(WebSearchProbe.classify(error)).isEqualTo("OTHER_ERROR");
    }
  }

  @Test void isolatedProbeEnablesWebSearchUsingOnlyTheSavedCredential() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    var document = factory.newDocumentBuilder().parse(Path.of("src/test/resources/web-search-probe.bpmn").toFile());
    var xpath = XPathFactory.newInstance().newXPath();
    assertThat(xpath.evaluate("//*[local-name()='process']/@id", document)).isEqualTo(WebSearchProbe.PROCESS_ID);
    assertThat(xpath.evaluate("//*[local-name()='input' and @target='provider.openai.backend.custom.openAiCustomCredential']/@source", document))
        .isEqualTo("=camunda.vars.env.cardDisputeLlm");
    assertThat(xpath.evaluate("//*[local-name()='input' and @target='provider.openai.api.completions.serverTools']/@source", document))
        .contains("web_search");
    assertThat(xpath.evaluate("//*[local-name()='input' and @target='provider.openai.api.type']/@source", document))
        .isEqualTo("completions");
    assertThat(xpath.evaluate("//*[local-name()='taskDefinition']/@retries", document)).isEqualTo("1");
    assertThat(xpath.evaluate("count(//*[local-name()='input' and (contains(@target,'apiKey') or contains(@target,'endpoint') or contains(@target,'model.model'))])", document))
        .isEqualTo("0");
  }
}
