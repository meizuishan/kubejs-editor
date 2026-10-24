package dev.kubejseditor.runtime;

public record ValidationIssue(String key,String field,String message) {
    public String pointer(){var match=java.util.regex.Pattern.compile("/[a-zA-Z0-9_]+(?:/[a-zA-Z0-9_]+)*").matcher(message);return match.find()?match.group():"";}
    public String text(){return key+"\n"+field+" · "+message;}
}
