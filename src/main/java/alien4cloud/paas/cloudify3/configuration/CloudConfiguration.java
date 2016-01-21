package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import alien4cloud.exception.NotFoundException;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@FormProperties({ "url", "locations" })
public class CloudConfiguration {

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url;

    @NotNull
    private LocationConfigurations locations;

    @JsonIgnore
    public LocationConfiguration getConfigurationLocation(String locationName) {
        switch (locationName) {
        case "amazon":
            return locations.getAmazon();
        case "openstack":
            return locations.getOpenstack();
        case "byon":
            return locations.getByon();
        }
        throw new NotFoundException("Location " + locationName + " not found");
    }
}
