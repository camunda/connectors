package io.camunda.connector.e2e;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.io.File;
import java.util.function.Function;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
        "spring.main.allow-bean-definition-overriding=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class ServiceNowTests {
  @Autowired
  private CamundaClient client;

  @Autowired
  Environment env;

  @TempDir
  File tempDir;

  String elementTemplatePath =
      "../../connectors/servicenow/element-templates/servicenow-connector.json";

  /**
   * Mutable pipeline context for passing function compositions
   */
  private static class PipelineContext {
    final String processId;
    final String serviceTaskName;
    final File filledTemplate;
    BpmnModelInstance model;
    BpmnModelInstance updatedModel;
    DeploymentEvent deploymentEvent;
    ProcessInstanceResult processInstanceResult;

    PipelineContext(String processId, String serviceTaskName, File filledTemplate) {
      this.processId = processId;
      this.serviceTaskName = serviceTaskName;
      this.filledTemplate = filledTemplate;
    }
  }

  private final Function<PipelineContext, PipelineContext> getModel = context -> {
    context.model = Bpmn.createProcess(context.processId)
        .executable()
        .startEvent()
        .serviceTask(context.serviceTaskName)
        .endEvent()
        .done();
    return context;
  };

  private final Function<PipelineContext, PipelineContext> updateModel = context -> {
    context.updatedModel = new BpmnFile(context.model)
        .writeToFile(new File(tempDir, java.util.UUID.randomUUID() + "-before.bpmn"))
        .apply(context.filledTemplate, context.serviceTaskName, new File(tempDir, java.util.UUID.randomUUID() + "-after.bpmn"));
    return context;
  };

  private final Function<PipelineContext, PipelineContext> deploy = context -> {
    context.deploymentEvent = client
        .newDeployResourceCommand()
        .addProcessModel(context.updatedModel, context.processId + ".bpmn")
        .send()
        .join();
    return context;
  };

  private final Function<PipelineContext, PipelineContext> execute = context -> {
    context.processInstanceResult = client
        .newCreateInstanceCommand()
        .bpmnProcessId(context.processId)
        .latestVersion()
        .withResult()
        .send()
        .join();
    return context;
  };


  @Test
  void incident() {
    String serviceTaskName = "sn-incident-create";
    String processId = "snCreateIncidentProcess";
    String resultVariable = "sysId";

    var elementTemplate =
        ElementTemplate.from(elementTemplatePath);
    var filledTemplate = elementTemplate
        .property("snInstance", env.getProperty("SN_INSTANCE"))
        .property("operationGroup", "create")
        .property("targetTable", "incident")
        .property("payload_incident", "={\"short_description\": \"Example incident created via API\"}")
        .property("authentication.username", env.getProperty("SN_USR"))
        .property("authentication.password", env.getProperty("SN_PWD"))
        .property("resultExpression", "={" + resultVariable + ": response.body.result.sys_id}")
        .writeTo(new File(tempDir, "servicenow-connector-incident.json"));

    PipelineContext pipeline = getModel
        .andThen(updateModel)
        .andThen(deploy)
        .andThen(execute)
        .apply(new PipelineContext(processId, serviceTaskName, filledTemplate));

    var variables = pipeline.processInstanceResult.getVariables();
    Assertions.assertNotNull(variables);
  }
}
