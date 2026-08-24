package pl.karolbystrek.kairos.api.operator;

import pl.karolbystrek.kairos.api.account.application.PlatformOperatorAccountService;
import pl.karolbystrek.kairos.api.account.application.exception.FinalPlatformOperatorConfirmationRequiredException;

import java.util.Arrays;

final class PlatformOperatorCommand {

    private static final String FINAL_DISABLE_CONFIRMATION = "DISABLE FINAL OPERATOR";

    private final PlatformOperatorAccountService accountService;
    private final OperatorCommandInput input;

    PlatformOperatorCommand(
        PlatformOperatorAccountService accountService,
        OperatorCommandInput input
    ) {
        this.accountService = accountService;
        this.input = input;
    }

    int run(String operation) {
        try {
            return switch (operation) {
                case "provision" -> provision();
                case "disable" -> disable();
                case "enable" -> enable();
                default -> {
                    input.writeLine("Usage: operator-account provision|disable|enable");
                    yield 64;
                }
            };
        }
        catch (RuntimeException exception) {
            input.writeLine("Platform Operator command failed: " + exception.getMessage());
            return 1;
        }
    }

    private int provision() {
        var username = input.readLine("Username: ");
        var email = input.readLine("Email: ");
        var password = input.readSecret("Password: ");
        var confirmation = input.readSecret("Confirm password: ");
        try {
            if (!Arrays.equals(password, confirmation)) {
                input.writeLine("Passwords do not match.");
                return 2;
            }
            var account = accountService.provision(username, email, new String(password));
            input.writeLine("Provisioned Platform Operator " + account.getUsername() + ".");
            return 0;
        }
        finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    private int disable() {
        var username = input.readLine("Username: ");
        try {
            accountService.disable(username, false);
        }
        catch (FinalPlatformOperatorConfirmationRequiredException exception) {
            input.writeLine("This is the final enabled Platform Operator.");
            var confirmation = input.readLine(
                "Type " + FINAL_DISABLE_CONFIRMATION + " to continue: "
            );
            if (!FINAL_DISABLE_CONFIRMATION.equals(confirmation)) {
                input.writeLine("Disable canceled.");
                return 2;
            }
            accountService.disable(username, true);
        }
        input.writeLine("Disabled Platform Operator " + username.strip().toLowerCase(java.util.Locale.ROOT) + ".");
        return 0;
    }

    private int enable() {
        var username = input.readLine("Username: ");
        accountService.enable(username);
        input.writeLine("Enabled Platform Operator " + username.strip().toLowerCase(java.util.Locale.ROOT) + ".");
        return 0;
    }
}
