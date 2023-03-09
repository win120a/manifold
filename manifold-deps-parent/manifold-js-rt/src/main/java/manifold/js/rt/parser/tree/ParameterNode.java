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

package manifold.js.rt.parser.tree;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class ParameterNode extends Node {
    private ArrayList<String> _params;
    private ArrayList<String> _types;

    public ParameterNode() {
        super(null);
        _params = new ArrayList<>();
        _types = new ArrayList<>();
    }

    //Takes in parameter and type in string form
    public void addParam(String param, String type) {
        _params.add(param);
        String paramType = (type != null && !type.isEmpty()) ? type : "java.lang.Object";
        _types.add(paramType);
    }

    public ArrayList<String> getTypes() {
        return _types;
    }

    public ArrayList<String> getParams() {
        return _params;
    }

    @Override
    public String genCode() {
        return _params.stream().collect(Collectors.joining(","));
    }
}
