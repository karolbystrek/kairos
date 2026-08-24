package pl.karolbystrek.kairos.api.operator;

import java.io.Console;

final class SystemOperatorCommandInput implements OperatorCommandInput {

    private final Console console;

    SystemOperatorCommandInput(Console console) {
        if (console == null) {
            throw new IllegalStateException(
                "A protected interactive console is required for Platform Operator commands"
            );
        }
        this.console = console;
    }

    @Override
    public String readLine(String prompt) {
        return console.readLine("%s", prompt);
    }

    @Override
    public char[] readSecret(String prompt) {
        return console.readPassword("%s", prompt);
    }

    @Override
    public void writeLine(String message) {
        console.writer().println(message);
        console.writer().flush();
    }
}
