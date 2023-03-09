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

package manifold.api.gen;

import java.lang.reflect.Modifier;
import java.util.*;
import javax.lang.model.element.ElementKind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.tree.Tree;
import manifold.api.host.IModule;
import manifold.api.type.ContributorKind;
import manifold.api.type.ITypeManifold;
import manifold.rt.api.util.ManClassUtil;

public class AbstractSrcClass<T extends AbstractSrcClass<T>> extends SrcStatement<T> {
    private final String _package;
    private final String _originalSimpleName;
    private final List<String> _imports;
    private final AbstractSrcClass.Kind _kind;
    private SrcType _superClass;
    private final AbstractSrcClass _enclosingClass;
    private final List<SrcType> _interfaces = new ArrayList<>();
    private final List<SrcField> _fields = new ArrayList<>();
    private final List<SrcField> _enumConsts = new ArrayList<>();
    private final List<SrcConstructor> _constructors = new ArrayList<>();
    private final List<AbstractSrcMethod> _methods = new ArrayList<>();
    private final List<SrcStatementBlock> _staticBlocks = new ArrayList<>();
    private final TreeMap<String, SrcGetProperty> _getProperties = new TreeMap<>();
    private final TreeMap<String, SrcSetProperty> _setProperties = new TreeMap<>();
    private final List<AbstractSrcClass> _innerClasses = new ArrayList<>();
    private final List<SrcType> _typeVars;

    private final IModule _module;
    private final JavaFileManager.Location _location;
    private final DiagnosticListener<JavaFileObject> _errorHandler;
    private boolean _binary;


    public AbstractSrcClass(String fqn, AbstractSrcClass.Kind kind) {
        this(fqn, null, kind);
    }

    public AbstractSrcClass(String fqn, AbstractSrcClass enclosingClass, AbstractSrcClass.Kind kind) {
        this(fqn, enclosingClass, kind, null, null, null);
    }

    public AbstractSrcClass(String fqn, AbstractSrcClass enclosingClass, AbstractSrcClass.Kind kind,
                            JavaFileManager.Location location, IModule module, DiagnosticListener<JavaFileObject> errorHandler) {
        super(enclosingClass);
        _enclosingClass = enclosingClass;
        _package = ManClassUtil.getPackage(fqn);
        _originalSimpleName = ManClassUtil.getShortClassName(fqn);
        disambiguateSimpleName(fqn);
        _kind = kind;
        _imports = new ArrayList<>();
        _typeVars = new ArrayList<>();

        _location = location;
        _module = module;
        _errorHandler = errorHandler;
    }

    /**
     * A nested class name must not duplicate an enclosing class name. If a duplicate is found,
     * the name is changed to a qualified name of the form: "EnclosingName_NestedName"
     */
    private void disambiguateSimpleName(String fqn) {
        String simpleName = ManClassUtil.getShortClassName(fqn);
        if (duplicatesEnclosing(simpleName)) {
            simpleName = _enclosingClass.getSimpleName() + '_' + simpleName;
        }
        name(simpleName);
    }

    private boolean duplicatesEnclosing(String simpleName) {
        if (_enclosingClass != null) {
            return _enclosingClass.getSimpleName().equals(simpleName) ||
                    _enclosingClass.duplicatesEnclosing(simpleName);
        }
        return false;
    }

    public String getDisambiguatedNameInNest(String name) {
        for (AbstractSrcClass inner : getInnerClasses()) {
            if (name.equals(inner._originalSimpleName)) {
                return inner.getSimpleName();
            }
        }
        return name;
    }

    public T superClass(SrcType superClass) {
        _superClass = superClass;
        return (T) this;
    }

    public T superClass(Class superClass) {
        _superClass = new SrcType(superClass);
        return (T) this;
    }

    public T superClass(String superClass) {
        _superClass = new SrcType(superClass);
        return (T) this;
    }

    public T addInterface(SrcType iface) {
        _interfaces.add(iface);
        iface.setOwner(this);
        return (T) this;
    }

    public T addInterface(Class iface) {
        SrcType t = new SrcType(iface);
        return addInterface(t);
    }

    public T addInterface(String iface) {
        SrcType t = new SrcType(iface);
        return addInterface(t);
    }

    public T addField(SrcField field) {
        _fields.add(field);
        field.setOwner(this);
        return (T) this;
    }

    public T addEnumConst(SrcField enumConst) {
        _enumConsts.add(enumConst);
        enumConst.setOwner(this);
        return (T) this;
    }

    public T addConstructor(SrcConstructor ctor) {
        _constructors.add(ctor);
        ctor.setOwner(this);
        return (T) this;
    }

    public T addMethod(AbstractSrcMethod method) {
        _methods.add(method);
        method.setOwner(this);
        return (T) this;
    }

    public T addGetProperty(SrcGetProperty property) {
        _getProperties.put(property.getSimpleName(), property);
        property.setOwner(this);
        return (T) this;
    }

    public T addSetProperty(SrcSetProperty property) {
        _setProperties.put(property.getSimpleName(), property);
        property.setOwner(this);
        return (T) this;
    }

    public T addInnerClass(AbstractSrcClass innerClass) {
        _innerClasses.add(innerClass);
        innerClass.setOwner(this);
        return (T) this;
    }

    public T addStaticBlock(SrcStatementBlock block) {
        _staticBlocks.add(block);
        block.setOwner(this);
        return (T) this;
    }

    public T imports(Class<?>... classes) {
        for (Class c : classes) {
            _imports.add(c.getName());
        }
        return (T) this;
    }

    public T imports(String... classes) {
        _imports.addAll(Arrays.asList(classes));
        return (T) this;
    }

    public T addImport(Class cls) {
        return addImport(cls.getTypeName());
    }

    public T addImport(String path) {
        _imports.add(path);
        return (T) this;
    }

    public T addStaticImport(String path) {
        _imports.add("static " + path);
        return (T) this;
    }

    public String getPackage() {
        return _package;
    }

    public AbstractSrcClass.Kind getKind() {
        return _kind;
    }

    public SrcType getSuperClass() {
        return _superClass;
    }

    public AbstractSrcClass<?> getEnclosingClass() {
        return _enclosingClass;
    }

    public List<SrcType> getInterfaces() {
        return _interfaces;
    }

    public List<SrcField> getFields() {
        return _fields;
    }

    public List<SrcField> getEnumConsts() {
        return _enumConsts;
    }

    public List<SrcConstructor> getConstructors() {
        return _constructors;
    }

    public List<AbstractSrcMethod> getMethods() {
        return _methods;
    }

    public List<SrcStatementBlock> getStaticBlocks() {
        return _staticBlocks;
    }

    public List<AbstractSrcClass> getInnerClasses() {
        return _innerClasses;
    }

    public void addTypeVar(SrcType typeVar) {
        _typeVars.add(typeVar);
    }

    public String getName() {
        return (_package.isEmpty() ? "" : (_package + '.')) + getSimpleName();
    }

    public boolean isInterface() {
        return _kind == Kind.Interface;
    }

    public boolean isEnum() {
        return _kind == Kind.Enum;
    }

    public boolean isAnnotation() {
        return _kind == Kind.Annotation;
    }

    public boolean isRecord() {
        return _kind == Kind.Record;
    }

    public List<SrcType> getTypeVariables() {
        return _typeVars;
    }

    /**
     * Is the source obtained from decompiling a binary form? If so, this usually
     * means the source will not be compiled to disk, but instead is used only in
     * the javac compiler to pose an existing type e.g., for extensions. The calls
     * to extension methods are rewritten to forward to extension class code.
     * </p>
     * The distinction is important because a binary-derived source can be stubbed.
     */
    public boolean isBinary() {
        return _binary || getEnclosingClass() != null && getEnclosingClass().isBinary();
    }

    public void setBinary(boolean binary) {
        _binary = binary;
    }

    public JavaFileManager.Location getLocation() {
        return getEnclosingClass() != null ? getEnclosingClass().getLocation() : _location;
    }

    public IModule getModule() {
        return getEnclosingClass() != null ? getEnclosingClass().getModule() : _module;
    }

    public DiagnosticListener<JavaFileObject> getErrorHandler() {
        return getEnclosingClass() != null ? getEnclosingClass().getErrorHandler() : _errorHandler;
    }

    public StringBuilder render() {
        return render(0);
    }

    public StringBuilder render(int indent) {
        return render(new StringBuilder(), indent);
    }

    @Override
    public StringBuilder render(StringBuilder sb, int indent) {
        return render(sb, indent, true);
    }

    public StringBuilder render(StringBuilder sb, int indent, boolean includePackage) {
        if (includePackage) {
            renderPackage(sb);
        }

        StringBuilder sbType = new StringBuilder();

        switch (_kind) {
            case Enum:
                renderEnum(sbType, indent);
                break;
            case Annotation:
                renderAnnotation(sbType, indent);
                break;
            case Class:
            case Interface:
                renderClassOrInterface(sbType, indent);
                break;
            case Record:
                renderRecord(sbType, indent);
                break;
        }

        if (getEnclosingClass() != null) {
            // add extensions to inner types
            supplementInner(getName(), sbType);
        }
        sb.append(sbType);
        return sb;
    }

    /**
     * Add extensions to inner classes
     */
    private void supplementInner(String fqnInner, StringBuilder innerSource) {
        if (getModule() == null || getLocation() == null) {
            // Location can be null within an IDE plugin. In this case the IDE must supplement classes its own way on its AST.
            return;
        }

        Set<ITypeManifold> sps = getModule().findTypeManifoldsFor(fqnInner);
        for (ITypeManifold sp : sps) {
            if (sp.getContributorKind() == ContributorKind.Supplemental) {
                String source = sp.contribute(getLocation(), fqnInner, isBinary(), innerSource.toString(), getErrorHandler());
                if (source != null) {
                    innerSource.replace(0, innerSource.length(), source);
                }
            }
        }
    }

    private void renderPackage(StringBuilder sb) {
        sb.append("/* Generated by Manifold */\n");
        if (!_package.isEmpty()) {
            sb.append("package ").append(_package).append(";\n\n");
        }
        sb.append('\n');
        for (String u : _imports) {
            sb.append("import ").append(u).append(";\n");
        }
    }

    private void renderAnnotation(StringBuilder sb, int indent) {
        renderAnnotations(sb, indent, false);
        indent(sb, indent);
        renderModifiers(sb, getModifiers() & ~(Modifier.FINAL | Modifier.ABSTRACT), false, Modifier.PUBLIC);
        sb.append("@interface ").append(getSimpleName())
                .append(" {\n");

        renderClassFeatures(sb, indent + INDENT);

        indent(sb, indent);
        sb.append("}\n\n");
    }

    private void renderEnum(StringBuilder sb, int indent) {
        renderAnnotations(sb, indent, false);
        indent(sb, indent);
        renderModifiers(sb, getModifiers() & ~Modifier.FINAL, false, Modifier.PUBLIC);
        sb.append("enum ").append(getSimpleName())
                .append(renderClassImplements(sb))
                .append(" {\n");

        renderEnumConstants(sb, indent + INDENT);
        renderClassFeatures(sb, indent + INDENT);

        indent(sb, indent);
        sb.append("}\n\n");
    }

    private void renderEnumConstants(StringBuilder sb, int indent) {
        for (int i = 0; i < _enumConsts.size(); i++) {
            SrcField c = _enumConsts.get(i);
            c.renderAnnotations(sb, indent, false);
            indent(sb, indent);
            sb.append(c.getSimpleName());
            if (i == _enumConsts.size() - 1) {
                sb.append(";\n\n");
            } else {
                sb.append(",\n");
            }
        }
    }

    private void renderClassOrInterface(StringBuilder sb, int indent) {
        renderAnnotations(sb, indent, false);
        indent(sb, indent);
        renderModifiers(sb, false, Modifier.PUBLIC);
        sb.append(_kind == AbstractSrcClass.Kind.Interface ? "interface " : "class ").append(getSimpleName()).append(renderTypeVars(_typeVars, sb))
                .append(genClassExtends(sb))
                .append(renderClassImplements(sb))
                .append(" {\n");

        renderClassFeatures(sb, indent + INDENT);

        indent(sb, indent);
        sb.append("}\n\n");
    }

    private void renderRecord(StringBuilder sb, int indent) {
        renderAnnotations(sb, indent, false);
        indent(sb, indent);
        renderModifiers(sb, false, Modifier.PUBLIC);
        sb.append("record ").append(getSimpleName()).append(renderTypeVars(_typeVars, sb));
        renderRecordParams(sb);
        sb.append(renderClassImplements(sb))
                .append(" {\n");

        renderClassFeatures(sb, indent + INDENT);

        indent(sb, indent);
        sb.append("}\n\n");
    }

    private StringBuilder renderRecordParams(StringBuilder sb) {
        AbstractSrcMethod ctor = findPrimaryConstructor();
        ctor.renderParameters(sb);
        return sb;
    }

    public AbstractSrcMethod findPrimaryConstructor() {
        return _methods.stream()
                .filter(c -> c.isPrimaryConstructor())
                .findFirst()
                .orElse(_constructors.stream()
                        .filter(m -> m.isPrimaryConstructor())
                        .findFirst().orElse(null));
    }

    private void renderClassFeatures(StringBuilder sb, int indent) {
        renderFields(sb, indent);
        renderConstructors(sb, indent);
        renderProperties(sb, indent);
        renderMethods(sb, indent);
        renderInnerClasses(sb, indent);
        renderStaticBlocks(sb, indent);
    }

    private String renderClassImplements(StringBuilder sb) {
        if (_interfaces.size() == 0) {
            return "";
        }

        if (getKind() == AbstractSrcClass.Kind.Interface) {
            sb.append(" extends ");
        } else {
            sb.append(" implements ");
        }

        for (int i = 0; i < _interfaces.size(); i++) {
            SrcType iface = _interfaces.get(i);
            sb.append(i > 0 ? ", " : "");
            iface.render(sb, 0);
        }
        return "";
    }

    private String genClassExtends(StringBuilder sb) {
        if (_superClass == null) {
            return "";
        }
        sb.append(" extends ");
        _superClass.render(sb, 0);
        return "";
    }

    private void renderFields(StringBuilder sb, int indent) {
//    sb.append( "\n" ).append( indent( sb, indent ) ).append( "// fields //\n" );
        for (SrcField field : _fields) {
            if (_kind == Kind.Record && !Modifier.isStatic((int) field.getModifiers())) {
                // only static fields allowed in records
                return;
            }

            field.render(sb, indent);
        }
    }

    private void renderMethods(StringBuilder sb, int indent) {
//    sb.append( "\n" ).append( indent( sb, indent ) ).append( "// methods //\n" );
        for (AbstractSrcMethod method : _methods) {
            method.render(sb, indent);
        }
    }

    private void renderStaticBlocks(StringBuilder sb, int indent) {
//    sb.append( "\n" ).append( indent( sb, indent ) ).append( "// static blocks //\n" );
        for (SrcStatementBlock block : _staticBlocks) {
            sb.append("\n").append(indent(sb, indent)).append("static {");
            block.render(sb, indent);
            sb.append("\n").append(indent(sb, indent)).append("}");
        }
    }

    private void renderConstructors(StringBuilder sb, int indent) {
//    sb.append( "\n" ).append( indent( sb, indent ) ).append( "// constructors //\n" );
        for (SrcConstructor ctor : _constructors) {
            ctor.render(sb, indent);
        }
    }

    private void renderProperties(StringBuilder sb, int indent) {
//    sb.append( "\n" ).append( indent( sb, indent ) ).append( "// properties //\n" );
        boolean separator = false;
        for (Map.Entry<String, SrcGetProperty> entry : _getProperties.entrySet()) {
            entry.getValue().render(sb, indent);
            SrcSetProperty srcSetProperty = _setProperties.get(entry.getKey());
            if (srcSetProperty != null) {
                srcSetProperty.render(sb, indent);
            }
            separator = true;
        }

        if (separator) {
            sb.append("\n");
            separator = false;
        }

        for (Map.Entry<String, SrcSetProperty> entry : _setProperties.entrySet()) {
            SrcGetProperty srcGetProperty = _getProperties.get(entry.getKey());
            if (srcGetProperty == null) {
                entry.getValue().render(sb, indent);
                separator = true;
            }
        }

        if (separator) {
            sb.append("\n");
        }
    }

//  private String getTypeVariables( StringBuilder sb )
//  {
//    if( _typeVars.isEmpty() )
//    {
//      return "";
//    }
//
//    sb.append( '<' );
//    for( int i = 0; i < gtvs.size(); i++ )
//    {
//      SrcTypeVar gtv = gtvs[i];
//      sb.append( i > 0 ? ", " : "" ).append( gtv.getSimpleName() );
//      String boundingType = gtv.getBoundingType();
//      if( boundingType != null )
//      {
//        sb.append( " extends ").append( boundingType );
//      }
//    }
//    sb.append( "> " );
//    return sb.toString();
//  }

    private void renderInnerClasses(StringBuilder sb, int indent) {
//    sb.append( "\n" ).append( indent( sb, indent ) ).append( "// inner classes //\n" );
        for (AbstractSrcClass innerClass : _innerClasses) {
            innerClass.render(sb, indent, false);
        }
    }

    public enum Kind {
        Class,
        Interface,
        Annotation,
        Enum,
        Record;

        public static Kind from(ElementKind kind) {
            switch (kind) {
                case ENUM:
                    return Enum;
                case CLASS:
                    return Class;
                case ANNOTATION_TYPE:
                    return Annotation;
                case INTERFACE:
                    return Interface;
                default:
                    if (kind.name().equals("RECORD")) {
                        return Record;
                    }
            }
            throw new IllegalArgumentException("Unhandled kind: " + kind);
        }

        public static Kind from(Tree.Kind kind) {
            switch (kind) {
                case ENUM:
                    return Enum;
                case CLASS:
                    return Class;
                case ANNOTATION_TYPE:
                    return Annotation;
                case INTERFACE:
                    return Interface;
                default:
                    if (kind.name().equals("RECORD")) {
                        return Record;
                    }
            }
            throw new IllegalArgumentException("Unhandled kind: " + kind);
        }
    }

}
