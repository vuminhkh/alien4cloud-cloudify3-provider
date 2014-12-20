package alien4cloud.paas.cloudify3.error;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Slf4j
public class CloudifyResponseErrorHandler extends DefaultResponseErrorHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    public CloudifyResponseErrorHandler() {
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        try {
            super.handleError(response);
        } catch (HttpStatusCodeException exception) {
            String formattedError = exception.getResponseBodyAsString();
            try {
                formattedError = objectMapper.writeValueAsString(objectMapper.readTree(formattedError));
                log.error("Rest error with body \n{}", formattedError);
            } catch (Exception e) {
                // Ignore if we cannot indent error
            }
            throw exception;
        }
    }
}
