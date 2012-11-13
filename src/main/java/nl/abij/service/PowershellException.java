package nl.abij.service;

import org.apache.commons.lang.StringUtils;

public class PowershellException extends RuntimeException {

    public PowershellException(String message) {
        super("Failed to execute:\ncommand: " + extract(message) + "\nmessage: " + pretty(message));
    }

    private static String pretty(String messageRaw) {
        String messageLower = stripControllChars(messageRaw).toLowerCase();
        String start = StringUtils.substringBefore(messageLower, " ").toLowerCase() + " : ";
        return StringUtils.substringBetween(messageLower, start, "at line:");
    }

    private static String extract(String messageRaw) {
        String message = stripControllChars(messageRaw);
        String command = StringUtils.substringBefore(message, " ");
        return StringUtils.substringBefore(message, command + " : ");
    }

    private static String stripControllChars(String messageRaw) {
        String result = messageRaw.replaceAll("\\p{Cntrl}", "");
        result = result.replaceAll("\\s?\\[\\d+;\\d?H", "");
        result = result.replaceAll("\\s?\\[\\d*;?\\d+?m", "");
        result = result.replaceAll("\\s\\s+", " ");
        return result;
    }
}
