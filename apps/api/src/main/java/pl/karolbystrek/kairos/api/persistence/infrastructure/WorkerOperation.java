package pl.karolbystrek.kairos.api.persistence.infrastructure;

public enum WorkerOperation {
    WEBHOOK_FANOUT, PUSH_FANOUT, WEBHOOK_CLAIM, WEBHOOK_COMPLETE,
    PUSH_CLAIM, PUSH_COMPLETE, SUBSCRIPTION_RETIRE, PUSH_CLEANUP
}
