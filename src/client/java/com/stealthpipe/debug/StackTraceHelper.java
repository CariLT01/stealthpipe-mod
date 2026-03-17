package com.stealthpipe.debug;

import java.io.PrintWriter;
import java.io.StringWriter;

public class StackTraceHelper {

    public static String getStackTraceAsString(Throwable throwable) {
        // Using try-with-resources to ensure writers are closed automatically
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            // Handle the unlikely event of an error during this process
            return "Error converting stack trace to string: " + e.getMessage();
        }
    }
}