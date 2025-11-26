package context;

import dto.BrokenCode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RegexProvider implements ContextProvider {
    protected Context context;
    protected final Pattern pattern;

    protected RegexProvider(Context context, Pattern pattern) {
        this.context = context;
        this.pattern = pattern;
    }

    public Context getContext() {
        return context;
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public boolean errorIsTargetedByProvider(LogParser.CompileError compileError, BrokenCode brokenCode) {
        return constructMatcher(compileError, brokenCode).find();
    }

    public Matcher findMatch(LogParser.CompileError compileError, BrokenCode brokenCode) {
        Matcher matcher = constructMatcher(compileError, brokenCode);

        if(!matcher.find()){
            throw new RuntimeException("Invalid call to find Match!");
        }

        return matcher;
    }

    public abstract Matcher constructMatcher(LogParser.CompileError compileError, BrokenCode brokenCode);
}
