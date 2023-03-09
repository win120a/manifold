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

package manifoldgraphql.extensions.manifold.graphql.sample.movies;

import manifold.graphql.sample.movies;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;

/**
 * Demonstrates extension methods on an inner class generated by a type manifold, in this case the using the GraphQL
 * Manifold for the {@code movies} type.
 */
@Extension
public class MyMoviesExt {
    public static class Person {
        public static String blahblahblah(@This movies.Person thiz) {
            return "blahblahblah";
        }

        public static class Builder {
            public static movies.Person.Builder withHeightInt(@This movies.Person.Builder thiz, int height) {
                return thiz.withHeight(height);
            }
        }
    }
}