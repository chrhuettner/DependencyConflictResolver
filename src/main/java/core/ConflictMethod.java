package core;

public class ConflictMethod {
    private final String methodName;
    private final String[] parameters;

    public ConflictMethod(String prompt) {
        int nameStartIndex = Math.max(0,prompt.indexOf("="));
        int nameEndIndex = prompt.indexOf("(", nameStartIndex + 1);
        methodName = prompt.substring(nameStartIndex, nameEndIndex);

        int parametersStartIndex = prompt.indexOf("(", nameEndIndex)+1;
        int parametersEndIndex = -1;
        int openPar = 1;
        int closedPar = 0;
        for (int i = parametersStartIndex; i < prompt.length(); i++) {
            char c = prompt.charAt(i);
            if (c == '(') {
                openPar++;
            }else if (c == ')') {
                closedPar++;
            }
            if(openPar == closedPar){
                parametersEndIndex = i;
                break;
            }
        }

        if(parametersEndIndex == -1){
            throw new RuntimeException("Could not find end of parameters in prompt "+prompt);
        }
        parameters = prompt.substring(parametersStartIndex, parametersEndIndex).split(",");
    }

    public String getMethodName() {
        return methodName;
    }

    public String[] getParameters() {
        return parameters;
    }
}
