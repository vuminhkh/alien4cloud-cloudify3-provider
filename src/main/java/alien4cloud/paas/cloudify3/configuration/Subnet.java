package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Subnet implements IaaSResource {

    @NotNull
    private String id;

    @NotNull
    private Integer ipVersion;

    @NotNull
    private String cidr;
}
