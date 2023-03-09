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
 * Use {@code @IncrementalCompile} to instruct Manifold to compile select resources incrementally.
 * For example, this annotation facilitates incremental compilation of changed resource files from
 * an IDE where the IDE knows which resource files have changed and need re/compile.
 */
@SuppressWarnings("unused")
@Retention(RetentionPolicy.SOURCE)
public @interface IncrementalCompile {
    /**
     * The qualified name of the driver class, which must implement {@code IIncrementalCompileDriver}.
     * Note this is not a {@code Class<? extends IIncrementalCompileDriver>} because the driver
     * class is likely not in the classpath of the compiler.
     */
    String driverClass();

    /**
     * The identity hash of the driver
     */
    int driverInstance();
}
