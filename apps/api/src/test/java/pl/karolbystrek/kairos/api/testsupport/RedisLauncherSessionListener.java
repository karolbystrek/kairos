package pl.karolbystrek.kairos.api.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class RedisLauncherSessionListener implements LauncherSessionListener {
    @Override
    public void launcherSessionClosed(LauncherSession session) {
        RedisTestContainer.stop();
    }
}
