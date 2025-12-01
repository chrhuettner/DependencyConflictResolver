package solver;

import context.LogParser;
import context.Context;
import dto.BrokenCode;
import dto.ErrorLocation;
import dto.ProposedChange;
import solver.deterministic.FinalClassSolver;
import solver.deterministic.ImportSolver;
import solver.deterministic.MethodParameterSolver;
import solver.deterministic.OverrideSolver;
import solver.nondeterministic.LLMCodeConflictSolver;
import type.ConflictType;

import java.util.ArrayList;
import java.util.List;

public interface CodeConflictSolver {
    boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation, List<ConflictType> conflictTypes);

    ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation);

    static List<CodeConflictSolver> getCodeConflictSolvers(Context context) {
        List<CodeConflictSolver> solvers = new ArrayList<>();
        solvers.add(new ImportSolver(context));
        solvers.add(new OverrideSolver(context));
        solvers.add(new FinalClassSolver(context));
        solvers.add(new MethodParameterSolver(context));
        solvers.add(new LLMCodeConflictSolver(context, context.getActiveProvider()));
        return solvers;
    }
}
