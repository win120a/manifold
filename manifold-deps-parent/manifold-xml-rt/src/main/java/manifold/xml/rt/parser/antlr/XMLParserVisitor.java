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

package manifold.xml.rt.parser.antlr;
// Generated from XMLParser.g4 by ANTLR 4.7

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link XMLParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 *            operations with no return type.
 */
public interface XMLParserVisitor<T> extends ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by {@link XMLParser#document}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitDocument(XMLParser.DocumentContext ctx);

    /**
     * Visit a parse tree produced by {@link XMLParser#prolog}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitProlog(XMLParser.PrologContext ctx);

    /**
     * Visit a parse tree produced by {@link XMLParser#content}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitContent(XMLParser.ContentContext ctx);

    /**
     * Visit a parse tree produced by {@link XMLParser#element}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitElement(XMLParser.ElementContext ctx);

    /**
     * Visit a parse tree produced by {@link XMLParser#reference}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitReference(XMLParser.ReferenceContext ctx);

    /**
     * Visit a parse tree produced by {@link XMLParser#attribute}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitAttribute(XMLParser.AttributeContext ctx);

    /**
     * Visit a parse tree produced by {@link XMLParser#chardata}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitChardata(XMLParser.ChardataContext ctx);

    /**
     * Visit a parse tree produced by {@link XMLParser#misc}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMisc(XMLParser.MiscContext ctx);
}
