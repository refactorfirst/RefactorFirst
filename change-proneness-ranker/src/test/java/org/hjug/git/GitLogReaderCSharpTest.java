package org.hjug.git;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitLogReaderCSharpTest {

    @TempDir
    File tempFolder;

    private Git git;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempFolder).call();
    }

    @AfterEach
    void tearDown() {
        git.getRepository().close();
    }

    @Test
    void givenSourcePaths_whenClassified_thenCSharpCountsAlongsideJavaAndKotlin() {
        assertTrue(GitLogReader.isAnalyzedSourceFile("src/Greeter.cs"));
        assertTrue(GitLogReader.isAnalyzedSourceFile("src/Main.java"));
        assertTrue(GitLogReader.isAnalyzedSourceFile("src/Main.kt"));
        assertFalse(GitLogReader.isAnalyzedSourceFile("src/App.csproj"));
        assertFalse(GitLogReader.isAnalyzedSourceFile("src/App.sln"));
        assertFalse(GitLogReader.isAnalyzedSourceFile("README.md"));
    }

    @Test
    void givenCommitsTouchingCsFiles_whenCounted_thenTheyAreIncludedAndOtherFilesAreNot() throws Exception {
        GitLogReader gitLogReader = new GitLogReader(git);

        write("Greeter.cs", "class Greeter { }");
        write("README.md", "hello");
        git.add().addFilepattern(".").call();
        RevCommit first = git.commit().setMessage("first").call();

        // Commits need different timestamps; they are keyed by commit time in seconds.
        Thread.sleep(1000);

        write("Greeter.cs", "class Greeter { int x; }");
        write("MessageStore.cs", "class MessageStore { }");
        write("README.md", "hello again");
        git.add().addFilepattern(".").call();
        RevCommit second = git.commit().setMessage("second").call();

        Map<Integer, Integer> counts = gitLogReader.captureChangeCountByCommitTimestamp();

        assertEquals(1, counts.get(first.getCommitTime()).intValue(), "Greeter.cs only; README.md is not source");
        assertEquals(2, counts.get(second.getCommitTime()).intValue(), "Greeter.cs changed, MessageStore.cs added");
    }

    @Test
    void givenACsFileCommittedTwice_whenItsLogIsRead_thenBothCommitsAreCounted() throws Exception {
        GitLogReader gitLogReader = new GitLogReader(git);

        write("Greeter.cs", "class Greeter { }");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("first").call();
        Thread.sleep(1000);
        write("Greeter.cs", "class Greeter { int x; }");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("second").call();

        ScmLogInfo log = gitLogReader.fileLog("Greeter.cs");

        assertEquals(2, log.getCommitCount());
    }

    private void write(String name, String content) throws IOException {
        Files.write(new File(git.getRepository().getWorkTree(), name).toPath(), content.getBytes(UTF_8));
    }
}
