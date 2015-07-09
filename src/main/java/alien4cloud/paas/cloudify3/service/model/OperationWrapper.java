package alien4cloud.paas.cloudify3.service.model;

import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.OperationOutput;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.model.PaaSNodeTemplate;

import com.google.common.collect.Maps;

/**
 * Wrapper for a real operation, give extension to deployment artifacts and others
 */
@AllArgsConstructor
public class OperationWrapper extends Operation {

    @Getter
    @Setter
    private IPaaSTemplate<?> owner;

    private Operation delegate;

    @Getter
    private String interfaceName;

    @Getter
    private String operationName;

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

    private PropertyEvaluatorService propertyEvaluatorService;

    private Map<String, PaaSNodeTemplate> allNodes;

    @Override
    public ImplementationArtifact getImplementationArtifact() {
        return delegate.getImplementationArtifact();
    }

    @Override
    public Map<String, IValue> getInputParameters() {
        if (delegate.getInputParameters() == null) {
            return Maps.newHashMap();
        }
        Map<String, IValue> inputs = Maps.newHashMap(delegate.getInputParameters());
        for (Map.Entry<String, IValue> input : inputs.entrySet()) {
            input.setValue(propertyEvaluatorService.process(input.getValue(), owner, allNodes));
        }
        return inputs;
    }

    public Set<OperationOutput> getOutputs() {
        return delegate.getOutputs();
    }

}
