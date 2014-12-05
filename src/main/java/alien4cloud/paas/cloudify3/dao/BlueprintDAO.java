package alien4cloud.paas.cloudify3.dao;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.concurrent.ListenableFuture;

import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.utils.FileUtil;

@Component
@Slf4j
public class BlueprintDAO extends AbstractDAO {

    public static final String BLUEPRINT_PATH = "/blueprints";

    @Override
    protected String getPath() {
        return BLUEPRINT_PATH;
    }

    public ListenableFuture<ResponseEntity<Blueprint[]>> asyncList() {
        log.info("List blueprint");
        return getRestTemplate().getForEntity(getBaseUrl(), Blueprint[].class);
    }

    @SneakyThrows
    public Blueprint[] list() {
        return asyncList().get().getBody();
    }

    @SneakyThrows
    public ListenableFuture<ResponseEntity<Blueprint>> asyncCreate(String id, String path) {
        log.info("Create blueprint {} with path {}", id, path);
        Path sourcePath = Paths.get(path);
        String sourceName = sourcePath.getFileName().toString();
        File destination = File.createTempFile(id, ".tar.gz");
        // Tar gz the parent directory
        FileUtil.tar(sourcePath.getParent(), destination.toPath(), true, false);
        if (log.isDebugEnabled()) {
            log.debug("Created temporary archive file at {} for blueprint {}", destination, id);
        }
        try {
            MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            ListenableFuture<ResponseEntity<Blueprint>> response = getRestTemplate().exchange(getSuffixedUrl("/{id}", "application_file_name"), HttpMethod.PUT,
                    new HttpEntity<>(Files.readAllBytes(destination.toPath()), headers), Blueprint.class, id, sourceName);
            if (log.isDebugEnabled()) {
                log.debug("Received response for upload {}", response);
            }
            return response;
        } finally {
            destination.delete();
        }
    }

    @SneakyThrows
    public Blueprint create(String id, String path) {
        return asyncCreate(id, path).get().getBody();
    }

    public ListenableFuture<ResponseEntity<Blueprint>> asyncRead(String id) {
        log.info("Read blueprint {}", id);
        return getRestTemplate().getForEntity(getSuffixedUrl("/{id}"), Blueprint.class, id);
    }

    @SneakyThrows
    public Blueprint read(String id) {
        return asyncRead(id).get().getBody();
    }

    public ListenableFuture<?> asyncDelete(String id) {
        log.info("Delete blueprint {}", id);
        return getRestTemplate().delete(getSuffixedUrl("/{id}"), id);
    }

    @SneakyThrows
    public void delete(String id) {
        asyncDelete(id).get();
    }
}
