package alien4cloud.paas.cloudify3.restclient;

import lombok.SneakyThrows;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.model.Version;
import alien4cloud.paas.cloudify3.util.FutureUtil;

import com.google.common.util.concurrent.ListenableFuture;

@Component
public class VersionClient extends AbstractClient {

    public static final String VERSION_PATH = "/version";

    @Override
    protected String getPath() {
        return VERSION_PATH;
    }

    public ListenableFuture<Version> asyncRead() {
        return FutureUtil.unwrapRestResponse(getForEntity(getBaseUrl(), Version.class));
    }

    @SneakyThrows
    public Version read() {
        return asyncRead().get();
    }
}
