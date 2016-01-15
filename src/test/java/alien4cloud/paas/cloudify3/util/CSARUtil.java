package alien4cloud.paas.cloudify3.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import alien4cloud.git.RepositoryManager;
import alien4cloud.model.components.Csar;
import alien4cloud.security.model.Role;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.FileUtil;

@Component
@Slf4j
public class CSARUtil {

    public static final String TOSCA_NORMATIVE_TYPES_NAME = "tosca-normative-types";

    public static final String URL_FOR_SAMPLES = "https://github.com/alien4cloud/samples.git";
    public static final String SAMPLES_TYPES_NAME = "samples";
    public static final String APACHE_TYPE_PATH = SAMPLES_TYPES_NAME + "/apache";
    public static final String MYSQL_TYPE_PATH = SAMPLES_TYPES_NAME + "/mysql";
    public static final String PHP_TYPE_PATH = SAMPLES_TYPES_NAME + "/php";
    public static final String WORDPRESS_TYPE_PATH = SAMPLES_TYPES_NAME + "/wordpress";
    public static final String TOMCAT_TYPE_PATH = SAMPLES_TYPES_NAME + "/tomcat-war";

    public static final Path ARTIFACTS_DIRECTORY = Paths.get("./target/csars");

    public static final Path TOSCA_NORMATIVE_TYPES = ARTIFACTS_DIRECTORY.resolve(TOSCA_NORMATIVE_TYPES_NAME);
    public static final String URL_FOR_NORMATIVES = "https://github.com/alien4cloud/tosca-normative-types.git";
    public static final String URL_FOR_STORAGE = "https://github.com/alien4cloud/alien4cloud-extended-types.git";
    public static final String ALIEN4CLOUD_STORAGE_TYPES = "alien4cloud-extended-types";
    public static final String ALIEN_EXTENDED_STORAGE_TYPES_1_0_SNAPSHOT = "alien-extended-storage-types-1.0-SNAPSHOT";
    public static final String ALIEN_EXTENDED_BASE_TYPES_1_0_SNAPSHOT = "alien-base-types-1.0-SNAPSHOT";

    @Resource
    private ArchiveUploadService archiveUploadService;

    private RepositoryManager repositoryManager = new RepositoryManager();

    public void uploadCSAR(Path path) throws Exception {
        log.info("Uploading csar {}", path);
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(path, zipPath);
        Authentication auth = new TestingAuthenticationToken(Role.ADMIN, "", Role.ADMIN.name());
        SecurityContextHolder.getContext().setAuthentication(auth);
        ParsingResult<Csar> result = archiveUploadService.upload(zipPath);
        if (result.getContext().getParsingErrors() != null && !result.getContext().getParsingErrors().isEmpty()) {
            boolean hasError = false;
            for (ParsingError error : result.getContext().getParsingErrors()) {
                log.error("Parsing error: " + error);
                if (error.getErrorLevel().equals(ParsingErrorLevel.ERROR)) {
                    hasError = true;
                }
            }
            if (hasError) {
                throw new RuntimeException("Parsing of csar failed");
            }
        }
        log.info("Uploaded csar {}", path);
    }

    public void uploadNormativeTypes() throws Exception {
        uploadCSAR(TOSCA_NORMATIVE_TYPES);
    }

    public void uploadApacheTypes() throws Exception {
        uploadCSAR(ARTIFACTS_DIRECTORY.resolve(APACHE_TYPE_PATH));
    }

    public void uploadMySqlTypes() throws Exception {
        uploadCSAR(ARTIFACTS_DIRECTORY.resolve(MYSQL_TYPE_PATH));
    }

    public void uploadPHPTypes() throws Exception {
        uploadCSAR(ARTIFACTS_DIRECTORY.resolve(PHP_TYPE_PATH));
    }

    public void uploadWordpress() throws Exception {
        uploadCSAR(ARTIFACTS_DIRECTORY.resolve(WORDPRESS_TYPE_PATH));
    }

    public void uploadStorage() throws Exception {
        uploadCSAR(ARTIFACTS_DIRECTORY.resolve(ALIEN4CLOUD_STORAGE_TYPES).resolve(ALIEN_EXTENDED_STORAGE_TYPES_1_0_SNAPSHOT));
        uploadCSAR(ARTIFACTS_DIRECTORY.resolve(ALIEN4CLOUD_STORAGE_TYPES).resolve(ALIEN_EXTENDED_BASE_TYPES_1_0_SNAPSHOT));
    }

    public void uploadTomcat() throws Exception {
        uploadCSAR(ARTIFACTS_DIRECTORY.resolve(TOMCAT_TYPE_PATH));
    }

    public void uploadArtifactTest() throws Exception {
        uploadCSAR(Paths.get("./src/test/resources/components/artifact-test"));
    }

    public void uploadCustomFS() throws Exception {
        uploadCSAR(Paths.get("./src/test/resources/components/support-hss"));
    }

    public void uploadAll() throws Exception {
        repositoryManager.cloneOrCheckout(ARTIFACTS_DIRECTORY, URL_FOR_SAMPLES, "orchestrator-refactoring", SAMPLES_TYPES_NAME);
        repositoryManager.cloneOrCheckout(ARTIFACTS_DIRECTORY, URL_FOR_NORMATIVES, "1.0.0.wd06.alien", TOSCA_NORMATIVE_TYPES_NAME);
        repositoryManager.cloneOrCheckout(ARTIFACTS_DIRECTORY, URL_FOR_STORAGE, "orchestrator-refactoring", ALIEN4CLOUD_STORAGE_TYPES);
        uploadNormativeTypes();
        uploadStorage();
        uploadTomcat();
        uploadApacheTypes();
        uploadMySqlTypes();
        uploadPHPTypes();
        uploadWordpress();
        uploadArtifactTest();
        uploadCustomFS();
    }
}
