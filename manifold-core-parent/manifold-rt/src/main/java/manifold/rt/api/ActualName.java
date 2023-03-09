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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Use this annotation in projected source code to indicate the actual name of a feature as originally specified.
 * For instance, if a specified name is not an acceptable Java identifier name, you'll have to modify the name
 * in your code. It is important to preserve this information for use with IDE tooling.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ActualName {
    /**
     * @return The actual name as originally specified in the system of record
     */
    String value();
}
