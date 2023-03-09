/*
 * Copyright (c) 2021 - Manifold Systems LLC
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

package manifold.ext.props.infer;

import manifold.ext.props.middle.NameTypeCollisionClass;
import org.junit.Test;

public class NameTypeCollisionCompiledTest {
    @Test
    public void testNameTypeCollision() {
        NameTypeCollisionClass c = new NameTypeCollisionClass();
        // String[] errors = c.errors; // should not be a property due to collision of List<String> and String[]
    }
}
