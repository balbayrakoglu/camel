/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.core.osgi;

import java.util.Collections;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.converter.TypeConverterLoader;
import org.apache.camel.spi.Injector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class OsgiTypeConverter extends ServiceSupport implements TypeConverter, ServiceTrackerCustomizer {

    private static final Log LOG = LogFactory.getLog(OsgiTypeConverter.class);

    private final BundleContext bundleContext;
    private final Injector injector;
    private final ServiceTracker tracker;
    private volatile DefaultTypeConverter registry;

    public OsgiTypeConverter(BundleContext bundleContext, Injector injector) {
        this.bundleContext = bundleContext;
        this.injector = injector;
        this.tracker = new ServiceTracker(bundleContext, TypeConverterLoader.class.getName(), this);
    }

    public Object addingService(ServiceReference serviceReference) {
        TypeConverterLoader loader = (TypeConverterLoader) bundleContext.getService(serviceReference);
        try {
            loader.load(getRegistry());
        } catch (Throwable t) {
            LOG.debug("Error while loading type converter", t);
        }
        return loader;
    }

    public void modifiedService(ServiceReference serviceReference, Object o) {
    }

    public void removedService(ServiceReference serviceReference, Object o) {
        this.registry = null;
    }

    @Override
    protected void doStart() throws Exception {
        this.tracker.open();
    }

    @Override
    protected void doStop() throws Exception {
        this.tracker.close();
        this.registry = null;
    }

    public <T> T convertTo(Class<T> type, Object value) {
        return getRegistry().convertTo(type, value);
    }

    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        return getRegistry().convertTo(type, exchange, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return getRegistry().mandatoryConvertTo(type, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        return getRegistry().mandatoryConvertTo(type, exchange, value);
    }

    public DefaultTypeConverter getRegistry() {
        if (registry == null) {
            synchronized (this) {
                if (registry == null) {
                    registry = createRegistry();
                }
            }
        }
        return registry;
    }

    protected DefaultTypeConverter createRegistry() {
        DefaultTypeConverter reg = new DefaultTypeConverter(new DefaultPackageScanClassResolver() {
            @Override
            public Set<ClassLoader> getClassLoaders() {
                return Collections.emptySet();
            }
        }, injector, null);
        Object[] services = this.tracker.getServices();
        if (services != null) {
            for (Object o : services) {
                try {
                    ((TypeConverterLoader) o).load(reg);
                } catch (Throwable t) {
                    LOG.debug("Error while loading type converter", t);
                }
            }
        }
        return reg;
    }


}
