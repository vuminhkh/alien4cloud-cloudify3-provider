package alien4cloud.paas.cloudify3.service.model;

import alien4cloud.paas.model.PaaSNodeTemplate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("PMD.UnusedPrivateField")
public class MatchedPaaSComputeTemplate {

    private PaaSNodeTemplate paaSNodeTemplate;

    private String computeTemplateId;
}
