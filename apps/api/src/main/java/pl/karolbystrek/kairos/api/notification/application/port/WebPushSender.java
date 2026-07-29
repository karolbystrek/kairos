package pl.karolbystrek.kairos.api.notification.application.port;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushMessage;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushResult;

public interface WebPushSender {

    WebPushResult send(@NonNull WebPushMessage message);
}
