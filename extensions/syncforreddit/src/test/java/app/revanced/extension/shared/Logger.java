package app.revanced.extension.shared;

public class Logger {
    @FunctionalInterface
    public interface LogMessage {
        String buildMessageString();
    }

    public static void printDebug(LogMessage message) {
        printDebug(message, null);
    }

    public static void printDebug(LogMessage message, Exception ex) {
    }

    public static void printInfo(LogMessage message) {
        printInfo(message, null);
    }

    public static void printInfo(LogMessage message, Exception ex) {
    }

    public static void printException(LogMessage message) {
        printException(message, null);
    }

    public static void printException(LogMessage message, Throwable ex) {
        System.err.println(message.buildMessageString());
        if (ex != null) {
            ex.printStackTrace(System.err);
        }
    }
}
