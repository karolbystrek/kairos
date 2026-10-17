package pl.karolbystrek.kairos.api.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class PostgresLauncherSessionListener implements LauncherSessionListener {
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // Loads application-test.yaml on top of the main application.yaml in every Spring test.
        System.setProperty("spring.profiles.active", "test");
        PostgresTestDatabase.start();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        PostgresTestDatabase.stop();
    }
}
