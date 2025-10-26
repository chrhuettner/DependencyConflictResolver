package core;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.Enumeration;
import java.util.jar.JarEntry;

import org.objectweb.asm.*;


public class SpoonTest {
    public static void main(String[] args) {


        //launcher.buildModel();

        //CtModel model = launcher.getModel();


        /*for (CtType<?> ctClass : launcher.getFactory().Class().getAll()) {
            System.out.println("Class: " + ctClass.getQualifiedName());
            // check references to dependency types
            ctClass.getElements(e -> e instanceof CtTypeReference)
                    .forEach(e -> System.out.println("  Uses type: " + e));
        }*/

        //analyzeDependencies(jarFiles);

        System.out.println(getReturnTypeOfMethod("C:\\Users\\Chrisi\\OneDrive - HTL Mössingerstrasse\\Semester 8\\Compilerbau\\DependencyConflictResolver\\testFiles\\projectSources\\XSeries_832e0f184efdad0fcf15d14cb7af5e30239ff454", "", "parseEnchantment"));
    }

    public static String getReturnTypeOfMethodFromSourceCode(String dir, String className, String methodName) {
        Launcher launcher = new Launcher();

        launcher.addInputResource(dir);
        launcher.getEnvironment().setComplianceLevel(11);

        launcher.buildModel();

        CtModel model = launcher.getModel();

        for (CtType<?> type : model.getAllTypes()) {
            if (!type.getSimpleName().equals(className)) {
                continue;
            }
            for (CtMethod method : type.getMethods()) {
                if (method.getSimpleName().equals(methodName)) {
                    CtTypeReference<?> returnType = method.getType();
                    return returnType.getSimpleName();
                }

            }
        }

        return null;
    }


    public static String getReturnTypeOfMethod(String dir, String className, String methodName) {
        final String[] returnType = {getReturnTypeOfMethodFromSourceCode(dir, className, methodName)};

        if (returnType[0] != null) {
            return returnType[0];
        }

        File depDir = new File(dir + "\\tmp\\dependencies");
        File[] jarFiles = depDir.listFiles(f -> f.getName().endsWith(".jar"));
        for (File file : jarFiles) {
            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class") && entry.getName().startsWith(className)) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            ClassReader reader = new ClassReader(is);
                            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                    if (methodName.equals(name)) {
                                        returnType[0] = Type.getReturnType(descriptor).getClassName();
                                        System.out.println("Found in: " + entry.getName().replace("/", ".").replace(".class", ""));

                                    }
                                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                                }
                            }, 0);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        return returnType[0];
    }


    public static void analyzeDependencies(File[] jarFiles) {
        URL[] urls = Arrays.stream(jarFiles).map(f -> {
            try {
                return f.toURI().toURL();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);
        URLClassLoader cl = URLClassLoader.newInstance(urls); // all jars together
        for (File jar : jarFiles) {
            try (JarFile jarFile = new JarFile(jar)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                System.out.println("File: " + jar.getAbsolutePath());
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName().replace("/", ".").replace(".class", "");
                        System.out.println("Class: " + className);
                        try {
                            Class<?> clazz = cl.loadClass(className);
                            for (Method m : clazz.getDeclaredMethods()) {
                                if (m.getName().equals("parseEnchantment")) {
                                    System.out.println("Found in: " + className + " -> " + m);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}