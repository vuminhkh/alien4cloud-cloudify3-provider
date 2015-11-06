package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@FormProperties({"url"})
public class CloudConfiguration {

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://yourManagerIP:8100";

    // key : location name ,value : list imports
    private Map<String, List<String>> importsByLocation;
}
