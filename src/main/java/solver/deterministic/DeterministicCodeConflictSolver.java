package solver.deterministic;

import context.Context;
import solver.CodeConflictSolver;

public abstract class DeterministicCodeConflictSolver extends CodeConflictSolver {

    public DeterministicCodeConflictSolver(Context context) {
        super(context);
    }
}
