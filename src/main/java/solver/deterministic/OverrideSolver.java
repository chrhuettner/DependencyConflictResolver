package solver.deterministic;

import context.LogParser;
import context.Context;
import dto.BrokenCode;
import dto.ErrorLocation;
import dto.ProposedChange;

public class OverrideSolver extends DeterministicCodeConflictSolver{
    public OverrideSolver(Context context) {
        super(context);
    }

    @Override
    public boolean errorIsFixableBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return context.getCompileError().message.startsWith("method does not override or implement a method from a supertype");
    }

    @Override
    public boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return brokenCode.code().trim().startsWith("@Override");
    }

    @Override
    public ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        ProposedChange proposedChange = new ProposedChange(context.getStrippedClassName(), brokenCode.code().trim().substring("@Override".length()), context.getCompileError().file, brokenCode.start(), brokenCode.end());
        return proposedChange;
    }
}
