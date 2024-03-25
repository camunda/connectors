package io.camunda.connector.runtime.inbound.executable;

import java.util.List;

public interface InboundExecutableRegistry {

  void handleEvent(InboundExecutableEvent event);

  List<ActiveExecutableResponse> query(ActiveExecutableQuery query);
}
