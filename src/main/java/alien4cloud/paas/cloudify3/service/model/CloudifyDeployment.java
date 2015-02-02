package alien4cloud.paas.cloudify3.service.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.model.PaaSNodeTemplate;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@AllArgsConstructor
@NoArgsConstructor
public class CloudifyDeployment {

    private String deploymentId;

    private String recipeId;

    private List<MatchedPaaSComputeTemplate> computes;

    private List<MatchedPaaSTemplate<NetworkTemplate>> internalNetworks;

    private List<MatchedPaaSTemplate<NetworkTemplate>> externalNetworks;

    private List<MatchedPaaSTemplate<StorageTemplate>> volumes;

    private List<PaaSNodeTemplate> nonNatives;

    private List<IndexedNodeType> nonNativesTypes;

    private List<IndexedRelationshipType> nonNativesRelationshipTypes;

    private Map<String, PaaSNodeTemplate> allNodes;

}
