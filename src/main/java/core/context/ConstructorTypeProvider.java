package core.context;

import core.BrokenCode;
import core.Context;
import core.ErrorLocation;
import core.LogParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ConstructorTypeProvider extends CompileErrorRegexProvider {

    protected ConstructorTypeProvider(Context context) {
        super(context, Pattern.compile("constructor (\\S*) in class (\\S*) cannot be applied to given types"));
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        Matcher constructorTypesMatcher = constructMatcher(compileError, brokenCode);
        constructorTypesMatcher.find();

        String targetMethod = constructorTypesMatcher.group(1);
        String targetClass = constructorTypesMatcher.group(2);

        String[] parameterClassNames = null;
        if(compileError.details.containsKey("found")) {
            parameterClassNames = compileError.details.get("found").split(",");
        }

        return new ErrorLocation(targetClass, targetMethod, parameterClassNames);
    }
}
