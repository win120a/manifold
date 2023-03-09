/*
 * Copyright (c) 2019 - Manifold Systems LLC
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

package manifold.science.vector;

import org.junit.Test;


import static manifold.science.util.AngleConstants.*;
import static manifold.science.util.UnitConstants.m;

public class VectorMathTest {
    @Test
    public void testVectorAddition() {
        LengthVector l = 1 m E +1 m N +1 m W +1 m S;
        System.out.println(l.getMagnitude().toNumber());
    }
}
