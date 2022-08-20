package org.hjug.git;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GitLogReaderTest {
    // Borrowed bits and pieces from
    // https://gist.github.com/rherrmann/0c682ea327862cb6847704acf90b1d5d

    @TempDir
    public File tempFolder;

    private Git git;
    private Repository repository;

    @BeforeEach
    public void setUp() throws GitAPIException {
        git = Git.init().setDirectory(tempFolder).call();
        repository = git.getRepository();
    }

    @AfterEach
    public void tearDown() {
        repository.close();
    }

    @Test
    void testFileLog() throws IOException, GitAPIException, InterruptedException {
        // This path works when referencing the full Tobago repository
        // String filePath = "tobago-core/src/main/java/org/apache/myfaces/tobago/facelets/AttributeHandler.java";

        GitLogReader gitLogReader = new GitLogReader();

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream));

        git.add().addFilepattern(".").call();
        RevCommit firstCommit = git.commit().setMessage("message").call();

        // Sleeping for one second to guarantee commits have different time stamps
        Thread.sleep(1000);

        // write contents of updated file to original file
        InputStream resourceAsStream2 = getClass().getClassLoader().getResourceAsStream("AttributeHandler2.java");
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream2));

        git.add().addFilepattern(".").call();
        RevCommit secondCommit = git.commit().setMessage("message").call();

        ScmLogInfo scmLogInfo = gitLogReader.fileLog(repository, attributeHandler);

        Assertions.assertEquals(2, scmLogInfo.getCommitCount());
        Assertions.assertEquals(firstCommit.getCommitTime(), scmLogInfo.getEarliestCommit());
        Assertions.assertEquals(secondCommit.getCommitTime(), scmLogInfo.getMostRecentCommit());
    }

    @Test
    void testWalkFirstCommit() throws IOException, GitAPIException {
        GitLogReader gitLogReader = new GitLogReader();

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream));
        git.add().addFilepattern(".").call();
        RevCommit commit = git.commit().setMessage("message").call();

        Map<Integer, Integer> result = gitLogReader.walkFirstCommit(repository, commit);

        Assertions.assertTrue(result.containsKey(commit.getCommitTime()));
        Assertions.assertEquals(1, result.get(commit.getCommitTime()).intValue());
    }

    @Test
    void testCaptureChangCountByCommitTimestamp() throws Exception {
        GitLogReader gitLogReader = new GitLogReader();

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream));

        git.add().addFilepattern(".").call();
        RevCommit firstCommit = git.commit().setMessage("message").call();

        // Sleeping for one second to guarantee commits have different time stamps
        Thread.sleep(1000);

        // write contents of updated file to original file
        InputStream resourceAsStream2 = getClass().getClassLoader().getResourceAsStream("AttributeHandler2.java");
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream2));

        InputStream resourceAsStream3 = getClass().getClassLoader().getResourceAsStream("Attributes.java");
        writeFile("Attributes.java", convertInputStreamToString(resourceAsStream3));

        git.add().addFilepattern(".").call();
        RevCommit secondCommit = git.commit().setMessage("message").call();

        Map<Integer, Integer> commitCounts = gitLogReader.captureChangeCountByCommitTimestamp(repository);

        Assertions.assertEquals(1, commitCounts.get(firstCommit.getCommitTime()).intValue());
        Assertions.assertEquals(
                2, commitCounts.get(secondCommit.getCommitTime()).intValue());
    }

    private void writeFile(String name, String content) throws IOException {
        File file = new File(git.getRepository().getWorkTree(), name);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(content.getBytes(UTF_8));
        }
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }
}
