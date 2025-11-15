package solver.deterministic;

import core.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FinalClassSolver extends DeterministicCodeConflictSolver {

    public FinalClassSolver(Context context) {
        super(context);
    }

    @Override
    public boolean errorIsFixableBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return getClassIndex(brokenCode) != -1;
    }

    @Override
    public boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return getMatcher(compileError).find();
    }

    @Override
    public ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        int classIndex = getClassIndex(brokenCode);

        String proposedCode = brokenCode.code();
        proposedCode = proposedCode.substring(0, classIndex) + " final " + proposedCode.substring(classIndex + 1);

        ProposedChange proposedChange = new ProposedChange(context.getStrippedClassName(), proposedCode, context.getCompileError().file, brokenCode.start(), brokenCode.end());

        return proposedChange;
    }

    private Matcher getMatcher(LogParser.CompileError compileError) {
        Pattern pattern = Pattern.compile("Class (\\S*) should be declared as final");
        Matcher matcher = pattern.matcher(compileError.message);
        return matcher;
    }

    private int getClassIndex(BrokenCode brokenCode) {
        return brokenCode.code().indexOf(" class ");
    }
}
