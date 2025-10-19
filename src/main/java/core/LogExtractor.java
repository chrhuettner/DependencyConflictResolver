package core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class LogExtractor {
    public static void main(String[] args) {
        HashSet<String> allErrors = new HashSet<>();
        String regex = ".*\\[[0-9]*,[0-9]*\\].*";
        AtomicInteger c = new AtomicInteger();
        Pattern pattern = Pattern.compile(regex);
        HashMap<String, int[]> classLookup = new HashMap<>();
        try (Stream<Path> paths = Files.walk(Paths.get("testFiles/brokenLogs"))) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            List<String> logFile = Files.readAllLines(path);
                            boolean loggedAnything = false;
                            boolean lastPrefixIsError = false;
                            String accumulatedErrorsMessage = "";
                            for (String logLine : logFile) {
                                if(logLine.startsWith("[")) {
                                    if(logLine.startsWith("[ERROR]")) {
                                        lastPrefixIsError = true;
                                    }else{
                                        lastPrefixIsError = false;
                                    }
                                    if(!accumulatedErrorsMessage.isEmpty()) {
                                        allErrors.add(accumulatedErrorsMessage);
                                        accumulatedErrorsMessage = "";
                                    }
                                }

                                //if(logLine.trim().startsWith("at ") || logLine.trim().startsWith("Caused by:")) {
                                //    continue;
                                //}





                                if(lastPrefixIsError) {
                                    //if(!pattern.matcher(logLine).matches()) {
                                   //     continue;
                                    //}
                                    accumulatedErrorsMessage += logLine+System.lineSeparator();
                                    loggedAnything = true;
                                }
                            }

                            if(!accumulatedErrorsMessage.isEmpty()) {
                                allErrors.add(accumulatedErrorsMessage);
                                loggedAnything = true;
                            }

                            if(!loggedAnything) {
                                //System.out.println("DID NOT LOG ANYTHING!!!");
                                c.getAndIncrement();
                            }

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println(c.get());

        try {
            Files.write(Paths.get("testFiles/allErrors.txt"), allErrors, Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
