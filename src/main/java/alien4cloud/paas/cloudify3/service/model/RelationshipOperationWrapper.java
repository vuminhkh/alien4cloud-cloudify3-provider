package alien4cloud.paas.cloudify3.service.model;

import lombok.Getter;
import alien4cloud.model.components.Operation;

public class RelationshipOperationWrapper extends OperationWrapper {

    @Getter
    private Relationship relationship;

    public RelationshipOperationWrapper(Relationship relationship, Operation delegate) {
        super(delegate);
        this.relationship = relationship;
    }
}
