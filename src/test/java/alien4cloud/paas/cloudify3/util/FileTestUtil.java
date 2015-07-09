package alien4cloud.paas.cloudify3.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import junitx.framework.FileAssert;
import alien4cloud.utils.FileUtil;

public class FileTestUtil {

    public static void assertFilesAreSame(final Path expected, final Path actual) throws IOException {
        Files.walkFileTree(expected, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileRelativePath = FileUtil.relativizePath(expected, file);
                Path destFile = actual.resolve(fileRelativePath);
                FileAssert.assertEquals(fileRelativePath + " should be same than recorded one", file.toFile(), destFile.toFile());
                return FileVisitResult.CONTINUE;
            }
        
        });
    }

}
