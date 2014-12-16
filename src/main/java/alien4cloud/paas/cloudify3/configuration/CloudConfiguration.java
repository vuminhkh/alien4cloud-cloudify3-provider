package alien4cloud.paas.cloudify3.configuration;

import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormValidValues;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Maps;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@FormProperties({ "provider", "url", "images", "flavors" })
public class CloudConfiguration {

    /**
     * Cloudify 3 Rest API URL
     */
    @FormValidValues("openstack")
    @NotNull
    private String provider = "openstack";

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://yourManagerIP:8100";

    private Set<Image> images;

    private Set<Flavor> flavors;

    /**
     * The mapping for compute template id --> template configuration (image + flavor)
     */
    @JsonIgnore
    public Map<String, CloudifyComputeTemplate> getComputeTemplates() {
        Map<String, CloudifyComputeTemplate> computeTemplates = Maps.newHashMap();
        if (images != null && flavors != null) {
            for (Image image : images) {
                for (Flavor flavor : flavors) {
                    computeTemplates.put(flavor.getName().replaceAll(" ", "_") + "_" + image.getName().replaceAll(" ", "_"),
                            new CloudifyComputeTemplate(image.getId(), flavor.getId()));
                }
            }
        }
        return computeTemplates;
    }
}
