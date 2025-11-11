package solver;

import core.*;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import provider.AIProvider;
import solver.deterministic.ImportSolver;
import solver.deterministic.OverrideSolver;
import solver.nondeterministic.LLMCodeConflictSolver;

import java.util.ArrayList;
import java.util.List;

import static core.BumpRunner.extractClassIfNotCached;

public abstract class CodeConflictSolver {

    protected Context context;


    public CodeConflictSolver(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public static List<CodeConflictSolver> getCodeConflictSolvers(Context context) {
        List<CodeConflictSolver> solvers = new ArrayList<>();
        solvers.add(new ImportSolver(context));
        solvers.add(new OverrideSolver(context));
        solvers.add(new LLMCodeConflictSolver(context, context.getActiveProvider()));
        return solvers;
    }

    public abstract boolean errorIsFixableBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation);

    public abstract boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation);

    public abstract ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation);
}
