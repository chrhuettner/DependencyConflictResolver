package core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.jimple.common.expr.JVirtualInvokeExpr;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.core.typehierarchy.TypeHierarchy;
import sootup.core.types.ClassType;
import sootup.core.views.View;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.PathBasedAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.language.JavaJimple;
import sootup.java.core.views.JavaView;

public class SootUpTest {
    public static void main(String[] args) {
        // Create a AnalysisInputLocation, which points to a directory. All class files will be loaded
        // from the directory
        Path pathToBinary = Paths.get("testFiles/compiled/XSeries_832e0f184efdad0fcf15d14cb7af5e30239ff454");
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation("testFiles/compiled/XSeries_832e0f184efdad0fcf15d14cb7af5e30239ff454/WorldwideChat.jar");

        // Create a view for project, which allows us to retrieve classes
        View view = new JavaView(inputLocation);

        TypeHierarchy typehierarchy = view.getTypeHierarchy();



        // Create a signature for the class we want to analyze
        ClassType classType = view.getIdentifierFactory().getClassType("com.expl0itz.worldwidechat.libs.com.cryptomorin.xseries.XEnchantment");

        JavaSootClass sootClass = (JavaSootClass) view.getClass(classType).get();

        System.out.println(sootClass.getMethodsByName("parseEnchantment"));


        for (JavaSootMethod method:sootClass.getMethods()){
            System.out.println(method.getReturnType());
        }

        System.out.println(sootClass.getName());

    }
}
