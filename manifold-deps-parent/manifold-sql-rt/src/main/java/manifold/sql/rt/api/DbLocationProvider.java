/*
 * Copyright (c) 2023 - Manifold Systems LLC
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

package manifold.sql.rt.api;

import manifold.rt.api.util.ServiceUtil;
import manifold.util.concurrent.LocklessLazyVar;

import java.util.HashSet;
import java.util.Set;

public interface DbLocationProvider
{
  String PROVIDED = "#provided";

  LocklessLazyVar<Set<DbLocationProvider>> PROVIDERS =
    LocklessLazyVar.make( () -> {
      Set<DbLocationProvider> registered = new HashSet<>();
      ServiceUtil.loadRegisteredServices( registered, DbLocationProvider.class, DbLocationProvider.class.getClassLoader() );
      return registered;
    } );

  enum Mode {CompileTime, Runtime, Unknown}

  String getLocation( Mode mode, String... args );
}
