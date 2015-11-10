package alien4cloud.paas.cloudify3.configuration;

import alien4cloud.ui.form.annotation.FormProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@FormProperties({"openstack", "aws"})
public class Imports {

    private List<String> openstack;

    private List<String> aws;
}
