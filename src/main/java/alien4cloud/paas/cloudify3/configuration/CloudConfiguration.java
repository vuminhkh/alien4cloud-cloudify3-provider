package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import alien4cloud.exception.NotFoundException;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@FormProperties({"url", "imports"})
public class CloudConfiguration {

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://yourManagerIP:8100";

    @NotNull
    private Imports imports;

    @JsonIgnore
    public List<String> getImportsByLocation(String locationName) {
        switch (locationName) {
            case "aws": return imports.getAws();
            case "openstack": return  imports.getOpenstack();
        }
        return Lists.newArrayList();
    }

}
