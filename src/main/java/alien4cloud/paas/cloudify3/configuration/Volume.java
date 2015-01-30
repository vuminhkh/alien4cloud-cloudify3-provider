package alien4cloud.paas.cloudify3.configuration;

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
public class Volume {

    @NotNull
    private String id;

    @NotNull
    private String name;

    @NotNull
    private Integer size;

    private String deviceName;

    private String location;

    private String fileSystem;
}
