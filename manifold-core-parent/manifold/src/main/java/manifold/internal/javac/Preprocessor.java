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

package manifold.internal.javac;

import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.util.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.tools.JavaFileObject;

import manifold.api.type.IPreprocessor;
import manifold.rt.api.util.ServiceUtil;
import manifold.util.JreUtil;
import manifold.util.ReflectUtil;
import manifold.util.concurrent.LocklessLazyVar;


public class Preprocessor {
    private static final Context.Key<Preprocessor> preprocessorKey = new Context.Key<>();
    private static final LocklessLazyVar<List<IPreprocessor>> _registeredPreprocessors =
            LocklessLazyVar.make(() -> {
                Set<IPreprocessor> registered = new HashSet<>();
                ServiceUtil.loadRegisteredServices(registered, IPreprocessor.class, Preprocessor.class.getClassLoader());
                // sort according to preferred order
                ArrayList<IPreprocessor> processors = new ArrayList<>(registered);
                processors.sort(Comparator.comparingInt(p -> p.getPreferredOrder().ordinal()));
                return processors;
            });

    private final ParserFactory _parserFactory;

    public static Preprocessor instance(Context context) {
        Preprocessor instance = context.get(preprocessorKey);
        if (instance == null) {
            instance = new Preprocessor(context);
        }
        return instance;
    }

    private Preprocessor(Context context) {
        // override the ParserFactory to support fragments in comments and string literals
        _parserFactory = (ParserFactory) ReflectUtil.method(
                "manifold.internal.javac.ManParserFactory_" + (JreUtil.isJava17orLater() ? 17 : 8),
                "instance", Context.class).invokeStatic(context);
    }

    public CharSequence process(JavaFileObject sourceFile, CharSequence input) {
        for (IPreprocessor preprocessor : Objects.requireNonNull(_registeredPreprocessors.get())) {
            input = preprocessor.process(sourceFile.toUri(), input);
        }
        return input;
    }
}