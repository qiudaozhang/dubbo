/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.metrics.filter;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.metrics.collector.DefaultMetricsCollector;
import org.apache.dubbo.rpc.BaseFilter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ScopeModelAware;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.INTERNAL_ERROR;

@Activate(group = {CONSUMER, PROVIDER}, order = Integer.MIN_VALUE + 100)
public class MetricsFilter implements Filter, BaseFilter.Listener, ScopeModelAware {

    private final static ErrorTypeAwareLogger LOGGER = LoggerFactory.getErrorTypeAwareLogger(MetricsFilter.class);

    private DefaultMetricsCollector collector = null;
    private MethodMetricsInterceptor metricsInterceptor;

    @Override
    public void setApplicationModel(ApplicationModel applicationModel) {
        collector = applicationModel.getBeanFactory().getBean(DefaultMetricsCollector.class);

        if (collector != null) {
            metricsInterceptor = new MethodMetricsInterceptor(collector.getMethodSampler());
        }
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (collector == null || !collector.isCollectEnabled()) {
            return invoker.invoke(invocation);
        }

        try {
            metricsInterceptor.beforeMethod(invocation);
        } catch (Throwable t) {
            LOGGER.warn(INTERNAL_ERROR, "", "", "Error occurred when beforeMethod.", t);
        }

        return invoker.invoke(invocation);
    }

    @Override
    public void onResponse(Result result, Invoker<?> invoker, Invocation invocation) {
        if (collector == null || !collector.isCollectEnabled()) {
            return;
        }
        try {
            metricsInterceptor.afterMethod(invocation, result);
        } catch (Throwable t) {
            LOGGER.warn(INTERNAL_ERROR, "", "", "Error occurred when afterMethod.", t);
        }
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        if (collector == null || !collector.isCollectEnabled()) {
            return;
        }
        try {
            metricsInterceptor.handleMethodException(invocation, t, false);
        } catch (Throwable t1) {
            LOGGER.warn(INTERNAL_ERROR, "", "", "Error occurred when handleMethodException.", t1);
        }
    }

}
