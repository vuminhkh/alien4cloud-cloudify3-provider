package alien4cloud.paas.cloudify3.error;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

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
        } catch (Throwable exception) {
            log.error("Exception happened with Rest API", exception);
            while (exception instanceof ExecutionException) {
                if (exception.getCause() != null) {
                    exception = exception.getCause();
                } else {
                    break;
                }
            }
            if (exception instanceof HttpStatusCodeException) {
                HttpStatusCodeException httpException = (HttpStatusCodeException) exception;
                String formattedError = httpException.getResponseBodyAsString();
                try {
                    formattedError = objectMapper.writeValueAsString(objectMapper.readTree(formattedError));
                } catch (Exception e) {
                    // Ignore if we cannot indent error
                }
                log.error("Rest error with body \n{}", formattedError);
                throw httpException;
            }
        }
    }
}
