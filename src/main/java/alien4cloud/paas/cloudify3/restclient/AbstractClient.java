package alien4cloud.paas.cloudify3.restclient;

import javax.annotation.Resource;

import lombok.Getter;
import lombok.Setter;

import org.springframework.web.client.AsyncRestTemplate;

import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;

public abstract class AbstractClient {

    @Resource
    @Getter
    @Setter
    private AsyncRestTemplate restTemplate;

    @Resource
    @Getter
    @Setter
    private CloudConfigurationHolder configurationHolder;

    /**
     * Get the url appended with the given suffix
     *
     * @param suffix path of the action
     * @param parameterNames all parameters' name
     * @return the url suffixed
     */
    public String getSuffixedUrl(String suffix, String... parameterNames) {
        String urlPrefix = configurationHolder.getConfiguration().getUrl() + getPath() + (suffix != null ? suffix : "");
        if (parameterNames != null && parameterNames.length > 0) {
            StringBuilder urlBuilder = new StringBuilder(urlPrefix);
            urlBuilder.append("?");
            for (String parameterName : parameterNames) {
                urlBuilder.append(parameterName).append("={").append(parameterName).append("}&");
            }
            urlBuilder.setLength(urlBuilder.length() - 1);
            return urlBuilder.toString();
        } else {
            return urlPrefix;
        }
    }

    /**
     * Get the base url
     *
     * @param parameterNames all parameters' name
     * @return the url
     */
    public String getBaseUrl(String... parameterNames) {
        return getSuffixedUrl(null, parameterNames);
    }

    protected abstract String getPath();
}
