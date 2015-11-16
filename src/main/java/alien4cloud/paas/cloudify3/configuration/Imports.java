package alien4cloud.paas.cloudify3.configuration;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormProperties;

@Getter
@Setter
@FormProperties({ "openstack", "amazon" })
public class Imports {

    private List<String> openstack;

    private List<String> amazon;
}
