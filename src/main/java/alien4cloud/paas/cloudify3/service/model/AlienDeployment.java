package alien4cloud.paas.cloudify3.service.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.container.model.topology.Topology;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@AllArgsConstructor
@NoArgsConstructor
public class AlienDeployment {

    private String deploymentId;

    private String recipeId;

    private Topology topology;

    private List<MatchedPaaSComputeTemplate> computes;

    private Map<String, PaaSNodeTemplate> nodes;
}
