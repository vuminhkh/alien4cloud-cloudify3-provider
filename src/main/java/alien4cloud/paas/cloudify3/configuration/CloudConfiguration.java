package alien4cloud.paas.cloudify3.configuration;

import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;

@Getter
@Setter
@FormProperties({ "url", "imports" })
public class CloudConfiguration {

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://yourManagerIP:8100";

    @NotNull
    private Imports imports;

    @JsonIgnore
    public List<String> getImportsByLocation(String locationName) {
        switch (locationName) {
        case "amazon":
            return imports.getAmazon();
        case "openstack":
            return imports.getOpenstack();
        }
        return Lists.newArrayList();
    }

}
