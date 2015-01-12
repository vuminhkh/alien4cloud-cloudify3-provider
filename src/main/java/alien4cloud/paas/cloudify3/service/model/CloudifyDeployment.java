package alien4cloud.paas.cloudify3.service.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    private List<MatchedPaaSNativeComponentTemplate> computes;

    private List<MatchedPaaSNativeComponentTemplate> internalNetworks;

    private List<MatchedPaaSNativeComponentTemplate> externalNetworks;

    private List<PaaSNodeTemplate> nonNatives;

    private List<IndexedNodeType> nonNativesTypes;

    private List<IndexedRelationshipType> nonNativesRelationshipTypes;

}
