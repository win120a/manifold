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

package manifold.rt.api;

import java.util.*;

public interface IBootstrap {
    /**
     * Generated classes statically initialize with a call to this method, regardless of whether or not the class is
     * generated for use statically.
     */
    @SuppressWarnings("unused")
    static boolean dasBoot() {
        boolean result = true;
        Set<IBootstrap> bootstraps = Bootstraps.get();
        for (IBootstrap bootstrap : bootstraps) {
            result &= bootstrap.boot();
        }
        return result;
    }

    /**
     * The implementor boots Manifold here. Note this method is called many time and should short-circuit once Manifold
     * is initialized.
     *
     * @return true if initialization succeeds
     */
    boolean boot();
}
