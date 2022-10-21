package io.camunda.connector.inbound;

import io.camunda.connector.inbound.operate.OperateClientLifecycle;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.exception.OperateException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
public class InboundConnectorTestConfiguration {

    @Bean
    @Primary
    public OperateClientLifecycle operateClientLifecycle() throws OperateException {
        CamundaOperateClient camundaOperateClientMock = mock(CamundaOperateClient.class);
        when(camundaOperateClientMock.searchProcessDefinitions(any())).thenReturn(new ArrayList<>());
        return new OperateClientLifecycle(camundaOperateClientMock);
    }

}
