package pl.karolbystrek.kairos.api.operator;

interface OperatorCommandInput {

    String readLine(String prompt);

    char[] readSecret(String prompt);

    void writeLine(String message);
}
