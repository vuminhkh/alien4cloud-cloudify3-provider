package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@FormProperties({"url"})
public class CloudConfiguration {

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://yourManagerIP:8100";

}
