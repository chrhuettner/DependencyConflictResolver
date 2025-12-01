package solver.deterministic;

import context.LogParser;
import context.Context;
import dto.BrokenCode;
import dto.ErrorLocation;
import dto.ProposedChange;
import solver.ContextAwareSolver;
import type.ConflictType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FinalClassSolver extends ContextAwareSolver {

    public FinalClassSolver(Context context) {
        super(context);
    }

    @Override
    public boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation, List<ConflictType> conflictTypes) {
        return conflictTypes.contains(ConflictType.PARENT_CLASS_SEALED) && getMatcher(compileError).find() &&  getClassIndex(brokenCode) != -1;
    }

    @Override
    public ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        int classIndex = getClassIndex(brokenCode);

        String proposedCode = brokenCode.code();
        proposedCode = proposedCode.substring(0, classIndex) + " final " + proposedCode.substring(classIndex + 1);

        return new ProposedChange(context.getStrippedClassName(), proposedCode, context.getCompileError().file, brokenCode.start(), brokenCode.end());
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
