package solver;

import context.Context;

public abstract class ContextAwareSolver implements CodeConflictSolver {
    protected Context context;

    public ContextAwareSolver(Context context) {
        this.context = context;
    }
}
