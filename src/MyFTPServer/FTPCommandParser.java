package MyFTPServer;

public class FTPCommandParser {
    private final String command;
    private final String argument;

    public FTPCommandParser(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            this.command = "";
            this.argument = "";
            return;
        }

        String[] parts = rawLine.trim().split("\\s+", 2);
        this.command = parts[0].toUpperCase();
        this.argument = parts.length > 1 ? parts[1] : "";
    }

    public String getCommand() {
        return command;
    }

    public String getArgument() {
        return argument;
    }
}