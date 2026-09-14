package org.hjug.git;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
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

        GitLogReader gitLogReader = new GitLogReader(git);

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

        ScmLogInfo scmLogInfo = gitLogReader.fileLog(attributeHandler);

        Assertions.assertEquals(2, scmLogInfo.getCommitCount());
        Assertions.assertEquals(firstCommit.getCommitTime(), scmLogInfo.getEarliestCommit());
        Assertions.assertEquals(secondCommit.getCommitTime(), scmLogInfo.getMostRecentCommit());
    }

    @Test
    void testFileLogReturnsPartialResultsWhenWalkFailsMidIteration() throws Exception {
        // A missing tree encountered mid-walk (e.g. in a partial clone) must not
        // discard the commits that were already read:  fileLog should return the
        // partial results gathered before the walk failed.
        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        String contents = convertInputStreamToString(resourceAsStream);

        writeFile(attributeHandler, contents);
        git.add().addFilepattern(".").call();
        git.commit().setMessage("message").call();

        Thread.sleep(1000);

        writeFile(attributeHandler, contents + "\n// second revision\n");
        git.add().addFilepattern(".").call();
        RevCommit newestCommit = git.commit().setMessage("message").call();

        Iterable<RevCommit> failingWalk = walkYieldingThenThrowing(
                List.of(newestCommit), new RevWalkException(new MissingObjectException(headId(), Constants.OBJ_TREE)));

        GitLogReader gitLogReader = new GitLogReader(gitWithLogs(failingWalk));

        ScmLogInfo scmLogInfo = Assertions.assertDoesNotThrow(() -> gitLogReader.fileLog(attributeHandler));

        Assertions.assertEquals(1, scmLogInfo.getCommitCount());
        Assertions.assertEquals(newestCommit.getCommitTime(), scmLogInfo.getEarliestCommit());
        Assertions.assertEquals(newestCommit.getCommitTime(), scmLogInfo.getMostRecentCommit());
    }

    @Test
    void testFileLogRethrowsNonMissingObjectWalkFailures() throws Exception {
        // Guard against silencing unrelated walk failures: only missing objects
        // should be tolerated, anything else must still propagate.
        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("message").call();

        Iterable<RevCommit> failingWalk =
                walkYieldingThenThrowing(Collections.emptyList(), new RevWalkException(new IOException("disk I/O")));

        GitLogReader gitLogReader = new GitLogReader(gitWithLogs(failingWalk));

        Assertions.assertThrows(RevWalkException.class, () -> gitLogReader.fileLog("AttributeHandler.java"));
    }

    /**
     * A Git whose successive log commands return the given iterables; the underlying
     * repository is real so that HEAD resolution works.
     */
    @SafeVarargs
    private final Git gitWithLogs(Iterable<RevCommit>... iterables) throws IOException, GitAPIException {
        Git mockGit = mock(Git.class);
        when(mockGit.getRepository()).thenReturn(repository);
        List<LogCommand> logCommands = new ArrayList<>();
        for (Iterable<RevCommit> revCommits : iterables) {
            LogCommand logCommand = mock(LogCommand.class);
            when(logCommand.add(any(ObjectId.class))).thenReturn(logCommand);
            when(logCommand.addPath(anyString())).thenReturn(logCommand);
            when(logCommand.call()).thenReturn(revCommits);
            logCommands.add(logCommand);
        }
        when(mockGit.log())
                .thenReturn(
                        logCommands.get(0),
                        logCommands.subList(1, logCommands.size()).toArray(new LogCommand[0]));
        return mockGit;
    }

    /** An iterable that yields the given commits and then fails, mimicking a truncated walk. */
    private static Iterable<RevCommit> walkYieldingThenThrowing(List<RevCommit> commits, RuntimeException failure) {
        return () -> new Iterator<RevCommit>() {
            private final Iterator<RevCommit> delegate = commits.iterator();

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public RevCommit next() {
                if (delegate.hasNext()) {
                    return delegate.next();
                }
                throw failure;
            }
        };
    }

    private ObjectId headId() throws IOException {
        ObjectId head = repository.resolve("HEAD");
        return head == null ? ObjectId.fromString("1111111111111111111111111111111111111111") : head;
    }

    @Test
    void testFileLogFallsBackToTotalCommitCountWhenFilteredWalkYieldsNothingDueToMissingObjects() throws Exception {
        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream));
        git.add().addFilepattern(".").call();
        RevCommit onlyCommit = git.commit().setMessage("message").call();

        Iterable<RevCommit> failingFilteredWalk = walkYieldingThenThrowing(
                Collections.emptyList(),
                new RevWalkException(new MissingObjectException(headId(), Constants.OBJ_TREE)));
        Iterable<RevCommit> pathlessWalk = Collections.singletonList(onlyCommit);

        GitLogReader gitLogReader = new GitLogReader(gitWithLogs(failingFilteredWalk, pathlessWalk));

        ScmLogInfo scmLogInfo = Assertions.assertDoesNotThrow(() -> gitLogReader.fileLog(attributeHandler));

        Assertions.assertEquals(1, scmLogInfo.getCommitCount());
        Assertions.assertEquals(onlyCommit.getCommitTime(), scmLogInfo.getEarliestCommit());
        Assertions.assertEquals(onlyCommit.getCommitTime(), scmLogInfo.getMostRecentCommit());
    }

    @Test
    void testFileLogWithMissingTreeFallsBackToTotalCommitCount() throws Exception {
        // Simulates a shallow clone whose tree objects are missing:  the filtered walk
        // yields nothing, so fileLog falls back to the total (tree-independent) history.
        GitLogReader gitLogReader = new GitLogReader(git);

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        String contents = convertInputStreamToString(resourceAsStream);

        writeFile(attributeHandler, contents);
        git.add().addFilepattern(".").call();
        RevCommit firstCommit = git.commit().setMessage("message").call();

        Thread.sleep(1000);

        writeFile(attributeHandler, contents + "\n// second revision\n");
        git.add().addFilepattern(".").call();
        RevCommit secondCommit = git.commit().setMessage("message").call();

        deleteLooseObject(secondCommit.getTree());

        ScmLogInfo scmLogInfo = Assertions.assertDoesNotThrow(() -> gitLogReader.fileLog(attributeHandler));

        Assertions.assertEquals(2, scmLogInfo.getCommitCount());
        Assertions.assertEquals(firstCommit.getCommitTime(), scmLogInfo.getEarliestCommit());
        Assertions.assertEquals(secondCommit.getCommitTime(), scmLogInfo.getMostRecentCommit());
    }

    @Test
    void testFileLogDoesNotFabricateHistoryForFileNeverCommitted() throws Exception {
        // An empty filtered walk with NO missing objects means the file simply has no
        // history:  the total commit count must NOT be substituted.
        GitLogReader gitLogReader = new GitLogReader(git);

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("message").call();

        ScmLogInfo scmLogInfo = Assertions.assertDoesNotThrow(() -> gitLogReader.fileLog("NeverCommitted.java"));

        Assertions.assertEquals(0, scmLogInfo.getCommitCount());
    }

    private void deleteLooseObject(ObjectId objectId) throws IOException {
        String objectName = objectId.getName();
        File looseObject = new File(
                new File(repository.getDirectory(), "objects"),
                objectName.substring(0, 2) + '/' + objectName.substring(2));
        org.junit.jupiter.api.Assumptions.assumeTrue(
                looseObject.exists(), "loose object " + objectName + " should exist in a fresh repository");
        if (!looseObject.delete()) {
            throw new IOException("Unable to delete loose object " + looseObject);
        }
    }

    @Test
    void testWalkFirstCommit() throws IOException, GitAPIException {
        GitLogReader gitLogReader = new GitLogReader(git);

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(attributeHandler);
        writeFile(attributeHandler, convertInputStreamToString(resourceAsStream));
        git.add().addFilepattern(".").call();
        RevCommit commit = git.commit().setMessage("message").call();

        Map<Integer, Integer> result = gitLogReader.walkFirstCommit(commit);

        Assertions.assertTrue(result.containsKey(commit.getCommitTime()));
        Assertions.assertEquals(1, result.get(commit.getCommitTime()).intValue());
    }

    @Test
    void testCaptureChangCountByCommitTimestamp() throws Exception {
        GitLogReader gitLogReader = new GitLogReader(git);

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

        Map<Integer, Integer> commitCounts = gitLogReader.captureChangeCountByCommitTimestamp();

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
