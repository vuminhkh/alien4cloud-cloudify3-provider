package alien4cloud.paas.cloudify3.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;

import javax.annotation.Resource;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component("cloudify-classpath-resources-loader-service")
public class ClasspathResourceLoaderService {

    @Resource
    private ApplicationContext applicationContext;

    public Path loadResourceFromClasspath(String resource) throws IOException {
        URI uri = applicationContext.getResource("classpath:" + resource).getURI();
        String uriStr = uri.toString();
        Path path = null;
        if (uriStr.contains("!")) {
            FileSystem fs = null;
            try {
                String[] array = uriStr.split("!");
                fs = FileSystems.newFileSystem(URI.create(array[0]), new HashMap<String, Object>());
                path = fs.getPath(array[1]);

                // Hack to avoid classloader issues
                Path createTempFile = Files.createTempFile("cloudify3", ".tmp");
                createTempFile.toFile().deleteOnExit();
                Files.copy(path, createTempFile, StandardCopyOption.REPLACE_EXISTING);

                path = createTempFile;
            } finally {
                if (fs != null) {
                    fs.close();
                }
            }
        } else {
            path = Paths.get(uri);
        }
        return path;
    }

    public ClassLoader getApplicationContextClassLoader() {
        return this.applicationContext.getClassLoader();
    }

}
