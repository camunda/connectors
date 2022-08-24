package io.camunda.connectors.inbound;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;

import java.io.ByteArrayInputStream;

public class InboundExporter implements Exporter {

  @Override
  public void configure(Context context) throws Exception {

    context.setFilter(new InboundExporterFilter());
  }

  @Override
  public void export(Record<?> record) {
    System.out.println(
        record.getRecordType() + " " + record.getIntent() + " " + record.getValueType());
    System.out.println(record.getValue());

    if (record.getIntent() == ProcessIntent.CREATED) {

      var value = (Process) record.getValue();

      BpmnModelInstance modelInstance =
          Bpmn.readModelFromStream(new ByteArrayInputStream(value.getResource()));

      System.out.println(Bpmn.convertToString(modelInstance));
    }
  }

  private class InboundExporterFilter implements Context.RecordFilter {
    @Override
    public boolean acceptType(RecordType recordType) {
      return recordType == RecordType.EVENT;
    }

    @Override
    public boolean acceptValue(ValueType valueType) {
      return valueType == ValueType.PROCESS;
    }
  }
}
