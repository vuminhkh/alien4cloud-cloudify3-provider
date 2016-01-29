package alien4cloud.paas.cloudify3.restclient.auth;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class AuthenticationInterceptor {

    private String userName;

    private String password;

    private String encodedCredentials;

    public <T> HttpEntity<T> addAuthenticationHeader(HttpEntity<T> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Basic " + this.encodedCredentials);
        if (!request.getHeaders().isEmpty()) {
            headers.putAll(request.getHeaders());
        }
        return new HttpEntity<>(request.getBody(), headers);
    }

    private String encodeCredentials() {
        if (userName != null && password != null) {
            String plainCredentials = userName + ":" + password;
            return new String(Base64.encode(plainCredentials.getBytes()));
        } else {
            return null;
        }
    }

    public void setUserName(String userName) {
        this.userName = userName;
        this.encodedCredentials = encodeCredentials();
    }

    public void setPassword(String password) {
        this.password = password;
        this.encodedCredentials = encodeCredentials();
    }
}
