/*
 * Copyright (c) 2018 - Manifold Systems LLC
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

package manifold.templates.rt.runtime;

import java.io.IOException;

public interface ILayout {
    ILayout EMPTY = new ILayout() {
        @Override
        public void header(Appendable buffer) {
        }

        @Override
        public void footer(Appendable buffer) {
        }
    };

    void header(Appendable buffer) throws IOException;

    void footer(Appendable buffer) throws IOException;
}
