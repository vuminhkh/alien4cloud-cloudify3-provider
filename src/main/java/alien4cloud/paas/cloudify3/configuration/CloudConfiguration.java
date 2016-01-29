package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.exception.NotFoundException;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormPropertyDefinition;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@FormProperties({ "url", "locations" })
public class CloudConfiguration {

    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url;

    @NotNull
    private LocationConfigurations locations;

    @NotNull
    private String userName;

    @FormPropertyDefinition(type = "string", isPassword = true, isRequired = false)
    private String password;

    @NotNull
    private Boolean disableVerification;

    @JsonIgnore
    public LocationConfiguration getConfigurationLocation(String locationName) {
        switch (locationName) {
        case "amazon":
            return locations.getAmazon();
        case "openstack":
            return locations.getOpenstack();
        }
        throw new NotFoundException("Location " + locationName + " not found");
    }
}
