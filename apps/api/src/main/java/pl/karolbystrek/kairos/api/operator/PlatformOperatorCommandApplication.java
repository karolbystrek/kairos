package pl.karolbystrek.kairos.api.operator;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import pl.karolbystrek.kairos.api.ApiApplication;
import pl.karolbystrek.kairos.api.account.application.PlatformOperatorAccountService;

import java.util.Map;

public final class PlatformOperatorCommandApplication {

    private PlatformOperatorCommandApplication() {
    }

    public static void main(String[] args) {
        var application = new SpringApplication(ApiApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setDefaultProperties(Map.of(
            "kairos.scheduling.enabled", "false",
            "spring.main.log-startup-info", "false"
        ));

        var exitCode = 1;
        try (var context = application.run()) {
            var operation = args.length == 1 ? args[0] : "";
            var command = new PlatformOperatorCommand(
                context.getBean(PlatformOperatorAccountService.class),
                new SystemOperatorCommandInput(System.console())
            );
            exitCode = command.run(operation);
        }
        catch (RuntimeException exception) {
            System.err.println("Platform Operator command failed: " + exception.getMessage());
        }
        System.exit(exitCode);
    }
}
