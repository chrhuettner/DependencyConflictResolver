package core;

import org.objectweb.asm.*;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SourceCodeAnalyzer {

    private String sourceDirectory;
    private Launcher launcher;
    private CtModel model;

    public SourceCodeAnalyzer(String sourceDirectory) {
        try {
            this.sourceDirectory = sourceDirectory;
            Launcher launcher = new Launcher();

            launcher.addInputResource(sourceDirectory);
            launcher.getEnvironment().setComplianceLevel(11);

            launcher.getEnvironment().setNoClasspath(true);
            launcher.getFactory().getEnvironment().setIgnoreDuplicateDeclarations(true);

            launcher.buildModel();

            model = launcher.getModel();
        } catch (Exception e) {
            System.err.println(sourceDirectory);
            e.printStackTrace();
        }
    }

    public String getReturnTypeOfMethodFromSourceCode(String className, String methodName) {
        if (model == null) {
            return null;
        }
        for (CtType<?> type : model.getAllTypes()) {
            //System.out.println("QUAL: "+type.getQualifiedName());
            if (!type.getQualifiedName().equals(className)) {
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

    public String getTypeOfFieldInClass(File directory, String className, String fieldName) {

        final String[] typeOfField = new String[1];

        className = className.replace(".", "/");

        File[] jarFiles = directory.listFiles(f -> f.getName().endsWith(".jar"));
        for (File file : jarFiles) {
            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    //System.out.println(entry.getName());
                    if (entry.getName().endsWith(className + ".class")) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            ClassReader reader = new ClassReader(is);
                            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                                    if(name.equals(fieldName)){
                                        typeOfField[0] = Type.getType(descriptor).getClassName();
                                    }
                                    return super.visitField(access, name, descriptor, signature, value);
                                }
                            }, 0);
                            if (typeOfField[0] != null) {
                                return typeOfField[0];
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        return null;
    }

    public String getReturnTypeOfMethod(String className, String methodName, String[] parameterTypeNames) {
       String returnTypeInSourceCode = getReturnTypeOfMethodFromSourceCode(className, methodName);

        if (returnTypeInSourceCode != null) {
            return returnTypeInSourceCode;
        }

        File depDir = new File(sourceDirectory + "/tmp/dependencies");
        String dependencyReturnType = getReturnTypeOfMethodFromDependencies(className, methodName, parameterTypeNames, depDir);

        if (dependencyReturnType != null) {
            return dependencyReturnType;
        }

        File natDir = new File("testFiles/Java_Src/src");

        String nativeJavaClassReturnType = getReturnTypeOfMethodFromDependencies(className, methodName, parameterTypeNames, natDir);

        if (nativeJavaClassReturnType != null) {
            return nativeJavaClassReturnType;
        }

        return null;
    }


    public String getReturnTypeOfMethodFromDependencies(String className, String methodName, String[] parameterTypes, File directory) {
        final String[] returnType = new String[1];

        final String[] returnTypeOfMethodWithSameNumberOfParameters = new String[1];

        className = className.replace(".", "/");

        File[] jarFiles = directory.listFiles(f -> f.getName().endsWith(".jar"));
        for (File file : jarFiles) {
            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    //System.out.println(entry.getName());
                    if (entry.getName().endsWith(className + ".class")) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            ClassReader reader = new ClassReader(is);
                            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                    //System.out.println(methodName);
                                    if (methodName.equals(name)) {

                                        System.out.println("Found in: " + entry.getName().replace("/", ".").replace(".class", ""));
                                        Type[] types = Type.getArgumentTypes(descriptor);
                                        if (parameterTypes.length == types.length) {
                                            boolean found = true;
                                            for (int i = 0; i < types.length; i++) {
                                                if (!types[i].getClassName().endsWith(parameterTypes[i])) {
                                                    found = false;
                                                }
                                            }
                                            if(found) {
                                                returnType[0] = Type.getReturnType(descriptor).getClassName();
                                            }else{
                                                returnTypeOfMethodWithSameNumberOfParameters[0] = Type.getReturnType(descriptor).getClassName();
                                            }
                                        }

                                    }
                                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                                }
                            }, 0);
                            if (returnType[0] != null) {
                                return returnType[0];
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        //return returnTypeOfMethodWithSameNumberOfParameters[0];
        return null;
    }
}
