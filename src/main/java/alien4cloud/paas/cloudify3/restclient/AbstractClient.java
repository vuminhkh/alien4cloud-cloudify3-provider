package alien4cloud.paas.cloudify3.restclient;

import javax.annotation.Resource;

import lombok.Getter;
import lombok.Setter;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;

import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.restclient.auth.AuthenticationInterceptor;

public abstract class AbstractClient {

    @Resource
    @Setter
    private AsyncRestTemplate restTemplate;

    @Resource
    @Getter
    @Setter
    private CloudConfigurationHolder configurationHolder;

    @Resource
    private AuthenticationInterceptor authenticationInterceptor;

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

    protected HttpEntity<?> createHttpEntity() {
        return createHttpEntity(new HttpHeaders());
    }

    protected HttpEntity<?> createHttpEntity(HttpHeaders httpHeaders) {
        return authenticationInterceptor.addAuthenticationHeader(new HttpEntity(httpHeaders));
    }

    protected <T> HttpEntity<T> createHttpEntity(T body, HttpHeaders httpHeaders) {
        return authenticationInterceptor.addAuthenticationHeader(new HttpEntity<>(body, httpHeaders));
    }

    protected <T> ListenableFuture<ResponseEntity<T>> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
        return restTemplate.exchange(url, HttpMethod.GET, createHttpEntity(), responseType, uriVariables);
    }

    protected ListenableFuture<?> delete(String url, Object... urlVariables) {
        return restTemplate.exchange(url, HttpMethod.DELETE, createHttpEntity(), (Class<?>) null, urlVariables);
    }

    protected <T> ListenableFuture<ResponseEntity<T>> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType,
            Object... uriVariables) {
        return restTemplate.exchange(url, method, authenticationInterceptor.addAuthenticationHeader(requestEntity), responseType, uriVariables);
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
