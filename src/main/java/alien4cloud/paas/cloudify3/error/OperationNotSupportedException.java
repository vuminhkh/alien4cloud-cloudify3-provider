package alien4cloud.paas.cloudify3.error;

import alien4cloud.paas.exception.PaaSTechnicalException;

public class OperationNotSupportedException extends PaaSTechnicalException {

    public OperationNotSupportedException(String message) {
        super(message);
    }
}
