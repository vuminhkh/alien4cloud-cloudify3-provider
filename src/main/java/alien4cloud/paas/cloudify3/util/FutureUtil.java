package alien4cloud.paas.cloudify3.util;

import java.util.concurrent.ExecutionException;

import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureAdapter;

/**
 * @author Minh Khang VU
 */
public class FutureUtil {

    public static <T> ListenableFuture<T> unwrap(ListenableFuture<ResponseEntity<T>> future) {
        return new ListenableFutureAdapter<T, ResponseEntity<T>>(future) {
            @Override
            protected final T adapt(ResponseEntity<T> response) throws ExecutionException {
                return response.getBody();
            }
        };
    }
}
