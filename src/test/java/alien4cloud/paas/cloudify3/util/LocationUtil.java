package alien4cloud.paas.cloudify3.util;

public class LocationUtil {

    /**
     * Get the type of the location on which to perform the test.
     * 
     * @return The type specified by the alien.cloudify.location.type java property or 'openstack' if none is specified.
     */
    public static String getType() {
        String locationType = System.getProperty("alien.cloudify.location.type");
        if (locationType == null) {
            locationType = "openstack";
        }
        return locationType;
    }
}
