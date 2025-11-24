package context;

import javax.lang.model.type.TypeMirror;
import javax.tools.*;

import com.sun.source.tree.*;
import com.sun.source.util.*;

import java.io.IOException;
import java.net.URI;
import java.util.*;

public class PrimitiveExpressionSolver {

    public static String getTypeOfPrimitiveExpression(String expression) {
        String code = "class Test { void m() { var primitiveExpression = " + expression + "; } }";

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fm, null,
                List.of("-proc:none"), null,
                List.of(new SimpleJavaFileObject(
                        URI.create("string:///Test.java"),
                        JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignore) {
                        return code;
                    }
                })
        );

        JavacTask javacTask = (JavacTask) task;
        Iterable<? extends CompilationUnitTree> asts = null;
        try {
            asts = javacTask.parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            javacTask.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String[] returnArray = new String[1];
        for (CompilationUnitTree ast : asts) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitVariable(VariableTree var, Void p) {
                    if (var.getName().toString().equals("primitiveExpression")) {
                        TreePath path = getCurrentPath();
                        TypeMirror type = Trees.instance(javacTask).getTypeMirror(path);
                        returnArray[0] = type.toString();
                    }
                    return null;
                }
            }.scan(ast, null);
            if(returnArray[0] != null){
                return returnArray[0];
            }
        }
        return null;
    }
}
