package solver.deterministic;

import core.*;

public class ImportSolver extends DeterministicCodeConflictSolver {

    public ImportSolver(Context context) {
        super(context);
    }

    // Only call after errorIsTargetedBySolver is true
    @Override
    public boolean errorIsFixableBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        // Whole package import
        if (errorLocation.className().endsWith("*")) {
            return false;
        }

        JarDiffUtil jarDiffUtil = JarDiffUtil.getInstance(context.getTargetPathOld().toString(), context.getTargetPathNew().toString());

        String alternative = jarDiffUtil.getAlternativeClassImport(errorLocation.className());

        return alternative != null;
    }

    @Override
    public boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return brokenCode.code().startsWith("import");
    }

    // Only call after errorIsFixableBySolver is true
    @Override
    public ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        JarDiffUtil jarDiffUtil = JarDiffUtil.getInstance(context.getTargetPathOld().toString(), context.getTargetPathNew().toString());

        String alternative = jarDiffUtil.getAlternativeClassImport(errorLocation.className());

        ProposedChange proposedChange = new ProposedChange(context.getStrippedClassName(), "import " + alternative + ";", context.getCompileError().file, brokenCode.start(), brokenCode.end());
        //context.getProposedChanges().add(proposedChange);
        //context.getErrorSet().put(brokenCode.code().trim(), proposedChange);
        return proposedChange;

    }


}
