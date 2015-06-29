package alien4cloud.paas.cloudify3.service.model;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.Operation;

/**
 * Wrapper for a real operation, give extension to deployment artifacts and others
 */
public class OperationWrapper extends Operation {

    private Operation delegate;

    public OperationWrapper(Operation delegate) {
        this.delegate = delegate;
    }

    /**
     * node id --> artifact_name --> artifact
     */
    @Getter
    @Setter
    private Map<String, Map<String, DeploymentArtifact>> allDeploymentArtifacts;

    /**
     * (id of the relationship, source node id) --> artifact_name --> artifact
     */
    @Getter
    @Setter
    private Map<Relationship, Map<String, DeploymentArtifact>> allRelationshipDeploymentArtifacts;

    @Override
    public ImplementationArtifact getImplementationArtifact() {
        return delegate.getImplementationArtifact();
    }

    @Override
    public Map<String, IValue> getInputParameters() {
        return delegate.getInputParameters();
    }
}
