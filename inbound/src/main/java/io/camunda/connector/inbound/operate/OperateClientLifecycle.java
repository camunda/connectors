/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
  package io.camunda.connector.inbound.operate;

import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.*;
import io.camunda.operate.exception.OperateException;
import io.camunda.operate.search.SearchQuery;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Lifecycle implementation that also directly acts as a CamundaOperateClient by delegating all methods to the
 * CamundaOperateClient that is controlled (and kept in the delegate field)
 *
 */
@Component
public class OperateClientLifecycle extends CamundaOperateClient implements SmartLifecycle, Supplier<CamundaOperateClient> {

  public static final int PHASE = 22222;
  protected boolean autoStartup = true;
  protected boolean running = false;
  protected boolean runningInTestContext = false;

  protected final OperateClientFactory factory;
  protected CamundaOperateClient delegate;

  @Autowired
  public OperateClientLifecycle(final OperateClientFactory factory) {
    this.factory = factory;
  }

  /**
   * Allows to set the delegate being used manually, helpful for test cases
   */
  public OperateClientLifecycle(final CamundaOperateClient delegate) {
    this.factory = null;
    this.delegate = delegate;
  }

  @Override
  public void start() {
    if (factory!=null) {
      try {
        delegate = factory.camundaOperateClient();
      } catch (OperateException e) {
        throw new RuntimeException("Could not start Camunda Operate Client: "+ e.getMessage(), e);
      }
      this.running = true;
    } else {
      // in test cases we have injected a delegate already
      runningInTestContext = true;
    }
  }

  @Override
  public void stop() {
    try {
      delegate = null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      running = false;
    }
  }


  @Override
  public CamundaOperateClient get() {
    if (!isRunning()) {
      throw new IllegalStateException("CamundaOperateClient is not yet created!");
    }
    return delegate;
  }

  @Override
  public boolean isAutoStartup() {
    return autoStartup;
  }


  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return PHASE;
  }

  @Override
  public ProcessDefinition getProcessDefinition(Long key) throws OperateException {
    return delegate.getProcessDefinition(key);
  }

  @Override
  public List<ProcessDefinition> searchProcessDefinitions(SearchQuery query) throws OperateException {
    return delegate.searchProcessDefinitions(query);
  }

  @Override
  public String getProcessDefinitionXml(Long key) throws OperateException {
    return delegate.getProcessDefinitionXml(key);
  }

  @Override
  public BpmnModelInstance getProcessDefinitionModel(Long key) throws OperateException {
    return delegate.getProcessDefinitionModel(key);
  }

  @Override
  public ProcessInstance getProcessInstance(Long key) throws OperateException {
    return delegate.getProcessInstance(key);
  }

  @Override
  public List<ProcessInstance> searchProcessInstances(SearchQuery query) throws OperateException {
    return delegate.searchProcessInstances(query);
  }

  @Override
  public FlownodeInstance getFlownodeInstance(Long key) throws OperateException {
    return delegate.getFlownodeInstance(key);
  }

  @Override
  public List<FlownodeInstance> searchFlownodeInstances(SearchQuery query) throws OperateException {
    return delegate.searchFlownodeInstances(query);
  }

  @Override
  public Incident getIncident(Long key) throws OperateException {
    return delegate.getIncident(key);
  }

  @Override
  public List<Incident> searchIncidents(SearchQuery query) throws OperateException {
    return delegate.searchIncidents(query);
  }

  @Override
  public Variable getVariable(Long key) throws OperateException {
    return delegate.getVariable(key);
  }

  @Override
  public List<Variable> searchVariables(SearchQuery query) throws OperateException {
    return delegate.searchVariables(query);
  }

  @Override
  public String getOperateUrl() {
    return delegate.getOperateUrl();
  }

  @Override
  public void setOperateUrl(String operateUrl) {
    delegate.setOperateUrl(operateUrl);
  }

  @Override
  public Header getAuthHeader() {
    return delegate.getAuthHeader();
  }

  @Override
  public void setAuthHeader(Header authHeader) {
    delegate.setAuthHeader(authHeader);
  }

  @Override
  public void setTokenExpiration(int tokenExpiration) {
    delegate.setTokenExpiration(tokenExpiration);
  }
}
