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
import java.util.Enumeration;
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

            launcher.buildModel();

            model = launcher.getModel();
        }catch (Exception e){
            System.err.println(sourceDirectory);
            e.printStackTrace();
        }
    }

    public String getReturnTypeOfMethodFromSourceCode(String className, String methodName) {
        if(model == null){
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


    public String getReturnTypeOfMethod(String className, String methodName) {
        final String[] returnType = {getReturnTypeOfMethodFromSourceCode(className, methodName)};

        if (returnType[0] != null) {
            return returnType[0];
        }

        className = className.replace(".", "/");

        //System.out.println("CLASS " +className);

        File depDir = new File(sourceDirectory + "\\tmp\\dependencies");
        File[] jarFiles = depDir.listFiles(f -> f.getName().endsWith(".jar"));
        for (File file : jarFiles) {
            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    //System.out.println(entry.getName());
                    if (entry.getName().endsWith(className+".class")) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            ClassReader reader = new ClassReader(is);
                            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                    //System.out.println(methodName);
                                    if (methodName.equals(name)) {
                                        returnType[0] = Type.getReturnType(descriptor).getClassName();
                                        //System.out.println("Found in: " + entry.getName().replace("/", ".").replace(".class", ""));

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
        return null;
    }
}
