package org.hjug.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitLogReaderGetRepoUrlTest {

    @TempDir
    Path tempDir;

    private Git git;
    private File projectBaseDir;

    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        projectBaseDir = tempDir.toFile();
        git = Git.init().setDirectory(projectBaseDir).call();
        Files.write(tempDir.resolve("test.txt"), "test content".getBytes());
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();
    }

    @AfterEach
    void tearDown() {
        git.close();
    }

    @Test
    void testGetRepoUrlWithGitHubSshOrigin() throws Exception {
        git.remoteAdd()
                .setName("origin")
                .setUri(new org.eclipse.jgit.transport.URIish("git@github.com:user/repo.git"))
                .call();

        try (GitLogReader gitLogReader = new GitLogReader(projectBaseDir)) {
            String repoUrl = gitLogReader.getRepoUrl();
            String commitHash = git.log().call().iterator().next().getName();
            assertTrue(repoUrl.startsWith("https://github.com/user/repo/blob/" + commitHash + "/"));
        }
    }

    @Test
    void testGetRepoUrlWithGitLabSshOrigin() throws Exception {
        git.remoteAdd()
                .setName("origin")
                .setUri(new org.eclipse.jgit.transport.URIish("git@gitlab.com:user/repo.git"))
                .call();

        try (GitLogReader gitLogReader = new GitLogReader(projectBaseDir)) {
            String repoUrl = gitLogReader.getRepoUrl();
            String commitHash = git.log().call().iterator().next().getName();
            assertTrue(repoUrl.startsWith("https://gitlab.com/user/repo/-/blob/" + commitHash + "/"));
        }
    }

    @Test
    void testGetRepoUrlWithBitBucketSshOrigin() throws Exception {
        git.remoteAdd()
                .setName("origin")
                .setUri(new org.eclipse.jgit.transport.URIish("git@bitbucket.org:user/repo.git"))
                .call();

        try (GitLogReader gitLogReader = new GitLogReader(projectBaseDir)) {
            String repoUrl = gitLogReader.getRepoUrl();
            String commitHash = git.log().call().iterator().next().getName();
            assertTrue(repoUrl.startsWith("https://bitbucket.org/user/repo/src/" + commitHash + "/"));
        }
    }

    @Test
    void testGetRepoUrlWithHttpsOrigin() throws Exception {
        git.remoteAdd()
                .setName("origin")
                .setUri(new org.eclipse.jgit.transport.URIish("https://github.com/user/repo.git"))
                .call();

        try (GitLogReader gitLogReader = new GitLogReader(projectBaseDir)) {
            String repoUrl = gitLogReader.getRepoUrl();
            String commitHash = git.log().call().iterator().next().getName();
            assertTrue(repoUrl.startsWith("https://github.com/user/repo/blob/" + commitHash + "/"));
        }
    }

    @Test
    void testGetRepoUrlWithNoOrigin() throws Exception {
        try (GitLogReader gitLogReader = new GitLogReader(projectBaseDir)) {
            String repoUrl = gitLogReader.getRepoUrl();
            assertEquals("", repoUrl);
        }
    }
}
