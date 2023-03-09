/*
 * Copyright (c) 2020 - Manifold Systems LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package manifold.rt.api.util;

import manifold.util.JreUtil;
import manifold.util.ReflectUtil;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

public class ServiceUtil {
    /**
     * Loads, but does not initialize, all <i>registered</i> services of type `C` managed by this module container.
     * A registered compiler task is discoverable in the META-INF/ directory as specified by {@link ServiceLoader}.
     */
    public static <C> Set<C> loadRegisteredServices(Set<C> services, Class<C> serviceClass, ClassLoader classLoader) {
        // Load from Thread Context Loader
        // (currently the IJ plugin creates loaders for accessing type manifolds from project classpath)

        ServiceLoader<C> loader = ServiceLoader.load(serviceClass);
        try {
            hackServiceLoaderToHandleProxyFactoryForJpms(loader, serviceClass, null);
            for (Iterator<C> iterator = loader.iterator(); iterator.hasNext(); ) {
                try {
                    C service = iterator.next();
                    if (isAbsent(services, service)) {
                        services.add(service);
                    }
                } catch (ServiceConfigurationError e) {
                    // not in the loader, check thread ctx loader next
                }
            }
        } catch (UnsupportedClassVersionError ignore) {
            // This happens when compiling from IntelliJ because it sets the context classloader with a loader having all
            // kinds of stuff that shouldn't be there, like plugin jars etc. So if the plugin jar is compiled with a newer
            // version of bytecode than the project that is building and happens to have a class that implements the service
            // here, an UnsupportedClassVersion love note results.
            // SymbolProvider is an example of this where separate implementations for IDE and compiler provide BuildVariantSymbols.
            // The problem is that the IDE class is in this context classloader path.
            //
            //ignore.printStackTrace(); // printing this for now, but swallowing the exception and using the normal loader next...
        }

        if (Thread.currentThread().getContextClassLoader() != classLoader) {
            // Also load from this loader
            loader = ServiceLoader.load(serviceClass, classLoader);
            hackServiceLoaderToHandleProxyFactoryForJpms(loader, serviceClass, classLoader);
            for (Iterator<C> iterator = loader.iterator(); iterator.hasNext(); ) {
                try {
                    C service = iterator.next();
                    if (isAbsent(services, service)) {
                        services.add(service);
                    }
                } catch (ServiceConfigurationError e) {
                    // avoid chicken/egg errors from attempting to build a module that self-registers a source producer
                    // it's important to allow a source producer module to specify its xxx.ITypeManifold file in its META-INF
                    // directory so that users of the source producer don't have to
                }
            }
        }

        return services;
    }

    private static <C> void hackServiceLoaderToHandleProxyFactoryForJpms(ServiceLoader<C> serviceLoader, Class<?> serviceClass, ClassLoader classLoader) {
        if (!JreUtil.isJava9Modular_runtime() || !serviceClass.getSimpleName().equals("IProxyFactory_gen")) {
            return;
        }
        // Handle IProxyFactory_gen services in module mode.
        //
        // In module mode the standard Java LazyClassPathLookupIterator requires module-info.java files to expose services
        // as "providers".  This here LazyClassPathLookupIterator lets us get them from META-INF/services, which is required
        // since they are generated and placed there. Otherwise, there is no way (I am aware of) to generate services that
        // are discoverable in module mode.
        ReflectUtil.field(serviceLoader, "lookupIterator1").set(
                ReflectUtil.constructor("manifold.util.LazyClassPathLookupIterator", Class.class, ClassLoader.class)
                        .newInstance(serviceClass, classLoader == null ? ReflectUtil.field(serviceLoader, "loader").get() : classLoader));
    }

    /**
     * @return True if {@code sp} is not contained within {@code sps}
     */
    static <C> boolean isAbsent(Set<C> services, C service) {
        for (C existingSp : services) {
            if (existingSp.getClass().equals(service.getClass())) {
                return false;
            }
        }
        return true;
    }
}
