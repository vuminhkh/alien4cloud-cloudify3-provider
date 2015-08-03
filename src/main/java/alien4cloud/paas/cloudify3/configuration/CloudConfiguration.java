package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormCustomType;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormValidValues;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@FormProperties({ "provider", "url", "cloudInit", "templateConfigurations" })
public class CloudConfiguration {

    /**
     * Cloudify 3 Rest API URL
     */
    @FormValidValues({ "openstack", "byon" })
    @NotNull
    private String provider = "openstack";

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://yourManagerIP:8100";

    /**
     * Normally the cloud init is associated to template, it's here just to be a workaround to wait for the solution
     */
    @FormCustomType("text")
    private String cloudInit;

    private ComputeTemplateConfiguration[] templateConfigurations;
}
