package alien4cloud.paas.cloudify3.configuration;

import java.util.Set;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("PMD.UnusedPrivateField")
public class Network implements IaaSResource {

    @NotNull
    private String id;

    @NotNull
    private String name;

    @NotNull
    private Boolean isExternal;

    private Set<Subnet> subnets;
}
