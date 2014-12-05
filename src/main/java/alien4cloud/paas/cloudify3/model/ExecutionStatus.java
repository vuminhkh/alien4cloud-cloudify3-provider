package alien4cloud.paas.cloudify3.model;

/**
 * @author Minh Khang VU
 */
public enum ExecutionStatus {

    terminated, failed, cancelled, pending, started, cancelling, force_cancelling;

    public static boolean isTerminated(ExecutionStatus status) {
        switch (status) {
        case terminated:
        case failed:
        case cancelled:
            return true;
        default:
            return false;
        }
    }
}
