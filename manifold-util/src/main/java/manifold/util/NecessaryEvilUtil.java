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

package manifold.util;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;

public class NecessaryEvilUtil {
    private static Unsafe UNSAFE = null;

    public static Unsafe getUnsafe() {
        if (UNSAFE != null) {
            return UNSAFE;
        }

        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (Throwable t) {
            throw new RuntimeException("The 'Unsafe' class is not accessible");
        }
    }

    public static void bypassJava9Security() {
        bypassJava9Security(true);
    }

    public static void bypassJava9Security(boolean fullJdk) {
        disableJava9IllegalAccessWarning();
        openModules(fullJdk);
    }

    public static void disableJava9IllegalAccessWarning() {
        // runtime
        disableJava9IllegalAccessWarning(NecessaryEvilUtil.class.getClassLoader());
        // compile-time
        disableJava9IllegalAccessWarning(Thread.currentThread().getContextClassLoader());
    }

    // Disable Java 9 warnings re "An illegal reflective access operation has occurred"
    public static void disableJava9IllegalAccessWarning(ClassLoader cl) {
        if (JreUtil.isJava8()) {
            return;
        }

        try {
            Class cls = Class.forName("jdk.internal.module.IllegalAccessLogger", false, cl);
            Field logger = cls.getDeclaredField("logger");
            getUnsafe().putObjectVolatile(cls, getUnsafe().staticFieldOffset(logger), null);
        } catch (Throwable ignore) {
        }
    }

    private static void openModules(boolean fullJdk) {
        if (JreUtil.isJava8()) {
            return;
        }

        try {
            Class<?> classModule = ReflectUtil.type("java.lang.Module");
            ReflectUtil.MethodRef addExportsOrOpens = ReflectUtil.method(classModule, "implAddExportsOrOpens", String.class, classModule, boolean.class, boolean.class);

            //
            // Module: manifold jars
            //
//      Object /*Module*/ manifoldModule = ReflectUtil.method( Class.class, "getModule" ).invoke( NecessaryEvilUtil.class );
            Object /*Module*/ manifoldModule = ReflectUtil.field("java.lang.Module", "EVERYONE_MODULE").getStatic();

            // Open select packages in java.base module for reflective access
            openRuntimeModules(addExportsOrOpens, manifoldModule);

            if (fullJdk) {
                // Open select packages in jdk.compiler for reflective access
                openCompilerModules(addExportsOrOpens, manifoldModule);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Error initializing Manifold", e);
        }
    }

    private static void openRuntimeModules(ReflectUtil.MethodRef addExportsOrOpens, Object manifoldModule) {
        //
        // Module: java.base
        //
        Object /*Module*/ javaBaseModule = ReflectUtil.method(Class.class, "getModule").invoke(String.class);
        addExportsOrOpens.invoke(javaBaseModule, "jdk.internal.loader", manifoldModule, true, true);
        addExportsOrOpens.invoke(javaBaseModule, "jdk.internal.module", manifoldModule, true, true);
        addExportsOrOpens.invoke(javaBaseModule, "jdk.internal.vm", manifoldModule, true, true);
        addExportsOrOpens.invoke(javaBaseModule, "jdk.internal.vm.annotation", manifoldModule, true, true);
        addExportsOrOpens.invoke(javaBaseModule, "java.lang", manifoldModule, true, true);
        addExportsOrOpens.invoke(javaBaseModule, "java.lang.invoke", manifoldModule, true, true);
        addExportsOrOpens.invoke(javaBaseModule, "java.lang.module", manifoldModule, true, true);
        addExportsOrOpens.invoke(javaBaseModule, "java.lang.reflect", manifoldModule, true, true); // for jailbreak
        addExportsOrOpens.invoke(javaBaseModule, "java.net", manifoldModule, true, true);


        //
        // Module: java.desktop (needed for testing manifold IJ plugin)
        //
        Class<?> Desktop = ReflectUtil.type("java.awt.Desktop", true);
        if (Desktop == null) {
            // Warn and continue
            //System.out.println( "\nWARNING: Failed to find class 'java.awt.Desktop'\n" );
            return;
        }
        Object /*Module*/ javaDesktop = ReflectUtil.method(Class.class, "getModule").invoke(Desktop);
        addExportsOrOpens.invoke(javaDesktop, "sun.awt", manifoldModule, true, true);
    }

    private static void openCompilerModules(ReflectUtil.MethodRef addExportsOrOpens, Object manifoldModule) {
        //
        // Module: jdk.compiler
        //
        Object /*Module*/ jdkCompilerModule = ReflectUtil.method(Class.class, "getModule")
                .invoke(ReflectUtil.type("com.sun.tools.javac.code.Symbol", true));
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.api", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.code", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.comp", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.file", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.jvm", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.main", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.model", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.parser", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.platform", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.processing", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.resources", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.tree", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkCompilerModule, "com.sun.tools.javac.util", manifoldModule, true, true);

        //
        // Module: jdk.javadoc
        //
        Class<?> HtmlDoclet = ReflectUtil.type("jdk.javadoc.internal.doclets.formats.html.HtmlDoclet", true);
        if (HtmlDoclet == null) {
            // Warn and continue
            //System.out.println( "\nWARNING: Failed to find class 'jdk.javadoc.internal.doclets.formats.html.HtmlDoclet'\n" );
            return;
        }
        Object /*Module*/ jdkJavadoc = ReflectUtil.method(Class.class, "getModule").invoke(HtmlDoclet);
        addExportsOrOpens.invoke(jdkJavadoc, "jdk.javadoc.internal.doclets.formats.html", manifoldModule, true, true);
        addExportsOrOpens.invoke(jdkJavadoc, "jdk.javadoc.internal.tool", manifoldModule, true, true);
        if (!JreUtil.isJava13orLater()) {
            // `com.sun.tools.doclets.standard.Standard` and `com.sun.tools.javadoc.main.Main` are removed in JDK 13
            addExportsOrOpens.invoke(jdkJavadoc, "com.sun.tools.javadoc.main", manifoldModule, true, true);
            addExportsOrOpens.invoke(jdkJavadoc, "com.sun.tools.doclets.standard", manifoldModule, true, true);
        }
    }

    public static void openModule(Context context, String moduleName) {
        try {
            Symbol moduleToOpen = (Symbol) ReflectUtil.method(Symtab.instance(context), "getModule", Name.class)
                    .invoke(Names.instance(context).fromString(moduleName));

            if (moduleToOpen == null) {
                // not modular java 9+
                return;
            }

            moduleToOpen.complete();

            Set<Symbol> rootModules = (Set<Symbol>) ReflectUtil.field(
                    ReflectUtil.method(ReflectUtil.type("com.sun.tools.javac.comp.Modules"), "instance", Context.class).invokeStatic(context), "allModules").get();

            for (Symbol rootModule : rootModules) {
                rootModule.complete();

                List<Object> requires = (List<Object>) ReflectUtil.field(rootModule, "requires").get();
                List<Object> newRequires = new ArrayList(requires);
                Object addedRequires = ReflectUtil.constructor("com.sun.tools.javac.code.Directive$RequiresDirective", ReflectUtil.type("com.sun.tools.javac.code.Symbol$ModuleSymbol")).newInstance(moduleToOpen);
                newRequires.add(addedRequires);
                requires = com.sun.tools.javac.util.List.from(newRequires);
                ReflectUtil.field(rootModule, "requires").set(requires);

                List<Object> exports = new ArrayList<>((Collection) ReflectUtil.field(moduleToOpen, "exports").get());
                for (Symbol pkg : (Iterable<Symbol>) ReflectUtil.field(moduleToOpen, "enclosedPackages").get()) {
                    if (pkg instanceof Symbol.PackageSymbol) {
                        //System.err.println( "PACKAGE: " + pkg );
                        Object exp = ReflectUtil.constructor("com.sun.tools.javac.code.Directive$ExportsDirective", Symbol.PackageSymbol.class, com.sun.tools.javac.util.List.class).newInstance(pkg,
                                com.sun.tools.javac.util.List.of(rootModule));
                        exports.add(exp);

                        ((Map) ReflectUtil.field(rootModule, "visiblePackages").get()).put(((Symbol.PackageSymbol) pkg).fullname, pkg);
                    }
                }
                ReflectUtil.field(moduleToOpen, "exports").set(com.sun.tools.javac.util.List.from(exports));

                Set readModules = (Set) ReflectUtil.field(moduleToOpen, "readModules").get();
                readModules.add(rootModule);
                ReflectUtil.field(moduleToOpen, "readModules").set(readModules);
            }

        } catch (Throwable e) {
            System.err.println("Failed to reflectively add-exports " + moduleName + "/* to root module[s], you must add the following argument to jave.exe:\n" +
                    "  --add-exports=" + moduleName + "/*=<root-module>\n");
            throw new RuntimeException(e);
        }
    }

    //## no need for shenanigans here since ReflectUtil uses Class#getDeclaredMethods0/Fields0 to bypass java12 blacklisting
//    of fields. Leaving this commented out code here in case it is needed to address future jdk changes.
//
//  static
//  {
//    makeReflectionGreatAgain();
//  }
//
//  // Shutdown Oracle's attempt at blacklisting fields and methods from reflection introduced in Java 12
//  private static void makeReflectionGreatAgain()
//  {
//    if( JreUtil.isJava12orLater() )
//    {
//      try
//      {
//        Unsafe unsafe = NecessaryEvilUtil.getUnsafe();
//
//        // Must approximate the AccessibleObject#override field offset (because it is one of the filtered fields)
//        long overrideOffset = AccessibleObject_layout.getOverrideOffset( unsafe );
//
//        // Reflection.class.reflectionData.declaredFields = Reflection.class.getDeclaredFields0()
//        // then you can use ReflectUtil to: Reflection.fieldFilterMap = emptyMap()
//        // now reflection is great again
//
//        Class<?> moduleClass = ReflectUtil.type( "java.lang.Module" );
//        Method addOpens = moduleClass.getDeclaredMethod( "implAddOpens", String.class, moduleClass );
//        unsafe.putObject( addOpens, overrideOffset, true );
//        ReflectUtil.MethodRef getModule = ReflectUtil.method( Class.class, "getModule" );
//        addOpens.invoke( getModule.invoke( String.class ), "jdk.internal.reflect", getModule.invoke( NecessaryEvilUtil.class ) );
//
//        Class<?> reflectionClass = ReflectUtil.type( "jdk.internal.reflect.Reflection" );
//        ReflectUtil.method( (Object)reflectionClass, "reflectionData" ).invoke(); // to ensure initialization of reflectionData
//        SoftReference reflectionData = (SoftReference)ReflectUtil.field( (Object)reflectionClass, "reflectionData" ).get();
//        ReflectUtil.LiveMethodRef getDeclaredFields0 = ReflectUtil.method( (Object)reflectionClass, "getDeclaredFields0", boolean.class );
//        ReflectUtil.field( reflectionData.get(), "declaredPublicFields" ).set( getDeclaredFields0.invoke( true ) );
//        ReflectUtil.field( reflectionData.get(), "declaredFields" ).set( getDeclaredFields0.invoke( false ) );
//
//        // erase the internal blacklist
//        ReflectUtil.field( reflectionClass, "fieldFilterMap" ).setStatic( Collections.emptyMap() );
//      }
//      catch( Throwable e )
//      {
//        throw new RuntimeException( e );
//      }
//    }
//  }
//
//  //## this strategy works, but I'm keeping this as a backup in case the above strategy is thwarted by a newer jdk
//  //    release such as if getDeclaredFields0 is removed or some other change.
//  //
//  //  static
//  //  {
//  //    if( JreUtil.isJava12orLater() )
//  //    {
//  //      try
//  //      {
//  //        // Shutdown Oracle's attempt at blacklisting fields and methods from reflection in Java 12
//  //        Class<?> hackClass = Class.forName( NecessaryEvilUtil.class.getPackage().getName() + ".ReflectionHack_12" );
//  //        Method hackReflection = hackClass.getMethod( "hackReflection" );
//  //        hackReflection.invoke( null );
//  //      }
//  //      catch( Throwable e )
//  //      {
//  //        throw new RuntimeException( e );
//  //      }
//  //    }
//  //  }
//
//  @SuppressWarnings({"unused", "WeakerAccess"})
//  static abstract class AccessibleObject_layout
//  {
//    boolean override;
//    volatile Object securityCheckCache;
//
//    static long getOverrideOffset( Unsafe unsafe )
//    {
//      try
//      {
//        return unsafe.objectFieldOffset( AccessibleObject_layout.class.getDeclaredField( "override" ) );
//      }
//      catch( NoSuchFieldException e )
//      {
//        throw new RuntimeException( e );
//      }
//    }
//  }
}
