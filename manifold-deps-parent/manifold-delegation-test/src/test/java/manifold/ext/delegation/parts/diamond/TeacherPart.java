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

package manifold.ext.delegation.parts.diamond;

import manifold.ext.delegation.rt.api.part;
import manifold.ext.delegation.rt.api.link;

public @part class TeacherPart implements Teacher {
    @link
    Person _person;
    private final Department _department;

    public TeacherPart(Person person, Department department) {
        _person = person;
        _department = department;
    }

    @Override
    public Department getDepartment() {
        return _department;
    }

    @Override
    public String getTitle() {
        return "Prof";
    }
}
