package alien4cloud.paas.cloudify3.configuration;

import javax.validation.constraints.NotNull;

import alien4cloud.ui.form.annotation.FormProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("PMD.UnusedPrivateField")
@FormProperties({ "id", "name", "size", "deviceName", "location", "fileSystem" })
public class Volume implements IaaSResource {

    @NotNull
    private String id;

    @NotNull
    private String name;

    @NotNull
    private Long size;

    private String deviceName;

    private String location;

    private String fileSystem;
}
