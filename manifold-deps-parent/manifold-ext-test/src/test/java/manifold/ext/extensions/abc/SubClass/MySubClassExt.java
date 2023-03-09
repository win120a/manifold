/*
 * Copyright (c) 2022 - Manifold Systems LLC
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

package manifold.ext.extensions.abc.SubClass;

import abc.SubClass;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;

@Extension
public class MySubClassExt {
    public static String myMethod(@This SubClass thiz) {
        return "myMethod";
    }
}