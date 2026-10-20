package demo;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.JobSelectors.byElementId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = GateApplication.class)
@CamundaSpringProcessTest
class DeterministicTest {
  @Autowired CamundaClient client;
  @Autowired CamundaProcessTestContext context;

  private ProcessInstanceEvent start(String version) throws Exception {
    return start(version, Map.of());
  }

  private ProcessInstanceEvent start(String version, Map<String, Object> extra) throws Exception {
    new Api(context.getCamundaRestAddress().toString(), false).post("/v2/cluster-variables/global",
        Map.of("name", "cardDisputeLlm", "kind", "JSON", "value", Map.of(
            "endpoint", "camunda.secrets.FAKE_ENDPOINT", "apiKey", "camunda.secrets.FAKE_API_KEY",
            "model", "camunda.secrets.FAKE_MODEL")));
    client.newDeployResourceCommand()
        .addResourceStringUtf8(Demo.text("card-dispute.bpmn"), "card-dispute.bpmn")
        .addResourceStringUtf8(Demo.text("review-dispute.form"), "review-dispute.form")
        .addResourceStringUtf8(Demo.text(version.equals("v1")
            ? "policies/dispute-compliance-policy.md" : "policies/v2/dispute-compliance-policy.md"), "dispute-compliance-policy.md")
        .send().join();
    var document = Map.of("documentId", "export", "storeId", "in-memory", "contentHash", "synthetic",
        "metadata", Map.of("fileName", "transactions-2026-09.csv"));
    var statement = Map.of("documentId", "statement", "storeId", "in-memory", "contentHash", "synthetic",
        "metadata", Map.of("fileName", "statement-2026-09.pdf", "contentType", "application/pdf"));
    var documents = new java.util.ArrayList<Map<String, Object>>();
    documents.add(statement);
    documents.add(document);
    for (String relative : SkillInputs.PATHS) {
      documents.add(Map.of("documentId", "synthetic-" + java.nio.file.Path.of(relative).getFileName(),
          "storeId", "in-memory", "contentHash", "synthetic",
          "metadata", Map.of("fileName", java.nio.file.Path.of(relative).getFileName().toString())));
    }
    var variables = Demo.variables(documents, "http://unused.invalid", "unused");
    variables.remove("cardRulesKey");
    variables.put("disputeFilingDate", "2026-10-20");
    variables.put("deadlineScriptSha256", SkillInputs.SCRIPT_SHA);
    variables.put("deadlineSkillManifest", SkillInputs.manifest(Demo.ROOT.resolve("skills/chargeback-deadline")));
    variables.putAll(extra);
    return client.newCreateInstanceCommand().bpmnProcessId("CardDispute").latestVersion()
        .variables(variables).send().join();
  }

  private void activate(String id, Map<String, Object> arguments) {
    var call = new java.util.HashMap<>(arguments);
    call.put("_meta", Map.of("id", UUID.randomUUID().toString(), "name", id));
    context.completeJobOfAdHocSubProcess(byElementId("DisputeAgent"), result ->
        result.activateElement(id).variables(Map.of("toolCall", call)));
  }

  private void finishAgent(String version) throws Exception {
    context.completeJobOfAdHocSubProcess(byElementId("DisputeAgent"),
        Map.of("agent", Map.of("responseText", "```json\n" + Demo.JSON.writeValueAsString(Demo.draft(version)) + "\n```")),
        result -> result.completionConditionFulfilled(true));
  }

  private void assertParsedDraft(ProcessInstanceEvent pi, String version) {
    assertThatProcessInstance(pi).hasActiveElements("ReviewDraftedDecision")
        .hasLocalVariable("ReviewDraftedDecision", "draft", Demo.draft(version));
  }

  @Test @DisplayName("D1 v1 case - all tools, pushed fraud reply, revise then accept - completed")
  void d1() throws Exception {
    var pi = start("v1");
    activate("ReconcileTransactions", Map.of("exportDocument", Map.of("documentId", "export",
        "storeId", "in-memory", "contentHash", "synthetic",
        "metadata", Map.of("fileName", "transactions-2026-09.csv")),
        "merchant", "Northwind Electronics", "amount", 482.17));
    context.completeJob(byElementId("Reconcile_ReadDocument"),
        Map.of("exportCsv", Demo.text("data/transactions-2026-09.csv")));
    assertThatProcessInstance(pi).hasCompletedElements("Reconcile_End")
        .hasLocalVariableSatisfiesExpression("DisputeAgent", "toolCallResults",
            "{r: toolCallResults[name = \"ReconcileTransactions\"][1].content, ok: r.duplicateConfirmed = true and r.overchargeAmount = 482.17 and r.matchCount = 2 and r.sourceDocument = \"transactions-2026-09.csv\"}.ok");
    assertThatProcessInstance(pi).hasLocalVariableSatisfiesExpression("DisputeAgent", "toolCallResults",
        "toolCallResults[name = \"ReconcileTransactions\"][1].content.sourceDocumentId = \"export\"");

    activate("AnalyzeInSandbox", Map.of("operation", "BASH", "command", "printf synthetic"));
    context.completeJob(byElementId("AnalyzeInSandbox"),
        Map.of("toolCallResult", Map.of("filingDeadline", "2027-01-13")));
    assertThatProcessInstance(pi).hasCompletedElements("AnalyzeInSandbox");

    activate("AskFraudRiskAgent", Map.of("question", "Assess the duplicate charge risk."));
    context.completeJob(byElementId("Fraud_Send"), Map.of("a2a",
        Map.of("taskId", "fraud-" + pi.getProcessInstanceKey(), "state", "TASK_STATE_SUBMITTED")));
    assertThatProcessInstance(pi).hasActiveElements("Fraud_Race");
    client.newPublishMessageCommand().messageName("FraudAgentReply")
        .correlationKey("fraud-" + pi.getProcessInstanceKey())
        .variables(Map.of("pushed", Map.of("taskId", "fraud-" + pi.getProcessInstanceKey(),
            "state", "TASK_STATE_COMPLETED", "answer", "LOW"))).send().join();
    assertThatProcessInstance(pi).hasCompletedElements("Fraud_Answer", "Fraud_End", "AskFraudRiskAgent");
    finishAgent("v1");
    assertParsedDraft(pi, "v1");
    context.completeUserTask("ReviewDraftedDecision",
        Map.of("analystVerdict", "revise", "analystFeedback", "Clarify the duplicate evidence."));
    assertThatProcessInstance(pi).hasActiveElements("DisputeAgent")
        .hasVariableSatisfiesExpression("followUpInput", "contains(followUpInput, \"Clarify the duplicate evidence.\")")
        .hasVariableSatisfiesExpression("disputeDocuments", "count(disputeDocuments) = 5")
        .hasVariable("disputeFilingDate", "2026-10-20")
        .hasVariable("deadlineScriptSha256", SkillInputs.SCRIPT_SHA);
    finishAgent("v1");
    assertParsedDraft(pi, "v1");
    context.completeUserTask("ReviewDraftedDecision", Map.of("analystVerdict", "accept"));
    assertThatProcessInstance(pi).isCompleted().hasCompletedElements("DisputeDecided");
  }

  @Test @DisplayName("D2 warm tone demo override - APPROVE draft extracted, accept - completed")
  void d2() throws Exception {
    var pi = start("v2");
    finishAgent("v2");
    assertParsedDraft(pi, "v2");
    context.completeUserTask("ReviewDraftedDecision", Map.of("analystVerdict", "accept"));
    assertThatProcessInstance(pi).isCompleted().hasCompletedElements("DisputeDecided");
  }

  @Test @DisplayName("D4 fraud agent silent - two-minute timeout then accept - completed")
  void d4() throws Exception {
    var pi = start("v1");
    activate("AskFraudRiskAgent", Map.of("question", "Assess the duplicate charge risk."));
    context.completeJob(byElementId("Fraud_Send"), Map.of("a2a",
        Map.of("taskId", "silent-" + pi.getProcessInstanceKey(), "state", "TASK_STATE_SUBMITTED")));
    assertThatProcessInstance(pi).hasActiveElements("Fraud_Race");
    context.increaseTime(Duration.ofSeconds(121));
    assertThatProcessInstance(pi).hasCompletedElements("Fraud_Timeout", "Fraud_TimeoutEnd", "AskFraudRiskAgent")
        .hasLocalVariableSatisfiesExpression("DisputeAgent", "toolCallResults",
            "toolCallResults[name = \"AskFraudRiskAgent\"][1].content.state = \"TIMEOUT\"");
    finishAgent("v1");
    assertParsedDraft(pi, "v1");
    context.completeUserTask("ReviewDraftedDecision", Map.of("analystVerdict", "accept"));
    assertThatProcessInstance(pi).isCompleted();
  }
}
