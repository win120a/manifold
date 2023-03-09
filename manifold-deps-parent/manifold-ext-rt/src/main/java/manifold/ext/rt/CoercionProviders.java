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

package manifold.ext.rt;

import java.util.*;

import manifold.ext.rt.api.ICoercionProvider;
import manifold.rt.api.util.ServiceUtil;
import manifold.util.concurrent.LocklessLazyVar;

public class CoercionProviders {
    private static final LocklessLazyVar<Set<ICoercionProvider>> _coercionProviders =
            LocklessLazyVar.make(() -> {
                Set<ICoercionProvider> registered = new HashSet<>();
                ServiceUtil.loadRegisteredServices(registered, ICoercionProvider.class, CoercionProviders.class.getClassLoader());
                return registered;
            });

    public static Set<ICoercionProvider> get() {
        return _coercionProviders.get();
    }
}