package pl.karolbystrek.kairos.api.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class PostgresLauncherSessionListener implements LauncherSessionListener {
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        PostgresTestDatabase.start();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        PostgresTestDatabase.stop();
    }
}
