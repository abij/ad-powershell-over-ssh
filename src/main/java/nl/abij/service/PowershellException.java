package nl.abij.service;

import org.apache.commons.lang.StringUtils;

public class PowershellException extends RuntimeException {

    public PowershellException(String message) {
        super("Failed to execute:\ncommand: " + extractCommand(message) + "\nmessage: " + pretty(message));
    }

    private static String extractCommand(String messageRaw) {
        String message = stripControllChars(messageRaw);
        return StringUtils.substringBefore(message, " ");
    }

    private static String pretty(String messageRaw) {
        String messageLower = stripControllChars(messageRaw).toLowerCase();
        String commandLower = extractCommand(messageRaw).toLowerCase();
        String start = StringUtils.substringBefore(commandLower, " ") + " : ";
        return StringUtils.substringBetween(messageLower, start, " : at line:");
    }

    private static String stripControllChars(String messageRaw) {
        String result;
        result = messageRaw.replaceAll("\\s?[\\d+;?[Hm]]", "");
        result = result.replaceAll("\\p{Cntrl}", "");
        result = result.replaceAll("\\s\\s+", " ");
        return result;
    }
}
