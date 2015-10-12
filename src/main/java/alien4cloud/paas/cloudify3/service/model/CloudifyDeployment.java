package alien4cloud.paas.cloudify3.service.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.wf.Workflow;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@AllArgsConstructor
@NoArgsConstructor
public class CloudifyDeployment {

    /**
     * This id is a human readable paaS id that will be used to identify deployment on cloudify 3
     */
    private String deploymentPaaSId;

    /**
     * This id is technical alien deployment id that will be used to generate events and send back to alien
     */
    private String deploymentId;

    private List<PaaSNodeTemplate> computes;

    private Map<String, PaaSNodeTemplate> computesMap;

    private List<PaaSNodeTemplate> nonNatives;

    private List<IndexedNodeType> nonNativesTypes;

    private List<IndexedRelationshipType> nonNativesRelationshipTypes;

    private Map<String, PaaSNodeTemplate> allNodes;

    /**
     * node id --> artifact_name --> artifact
     */
    private Map<String, Map<String, DeploymentArtifact>> allDeploymentArtifacts;

    /**
     * (id of the relationship, source node id) --> artifact_name --> artifact
     */
    private Map<Relationship, Map<String, DeploymentArtifact>> allRelationshipDeploymentArtifacts;

    private Map<String, String> providerDeploymentProperties;

    private Map<String, Workflow> workflows;
}
