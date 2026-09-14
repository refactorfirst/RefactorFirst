package org.hjug.git;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.NullOutputStream;

@Slf4j
public class GitLogReader implements AutoCloseable {

    static final String JAVA_FILE_TYPE = ".java";
    static final String KOTLIN_FILE_TYPE = ".kt";

    private Repository gitRepository;

    private Git git;

    public GitLogReader() {}

    public GitLogReader(File basedir) throws IOException {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(basedir);
        String gitIndexFileEnvVariable = System.getenv("GIT_INDEX_FILE");
        if (Objects.nonNull(gitIndexFileEnvVariable)
                && !gitIndexFileEnvVariable.trim().isEmpty()) {
            log.debug("Setting Index File based on Env Variable GIT_INDEX_FILE {}", gitIndexFileEnvVariable);
            repositoryBuilder = repositoryBuilder.setIndexFile(new File(gitIndexFileEnvVariable));
        }

        git = Git.open(repositoryBuilder.getGitDir());
        gitRepository = git.getRepository();
    }

    GitLogReader(Git git) {
        this.git = git;
        gitRepository = git.getRepository();
    }

    @Override
    public void close() throws Exception {
        git.close();
    }

    public static File getGitDir(File basedir) {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(basedir);
        return repositoryBuilder.getGitDir();
    }

    /**
     * Returns the current commit hash at HEAD
     *
     * @return the commit hash as a String, or null if HEAD cannot be resolved
     * @throws IOException if an I/O error occurs
     */
    public String getCurrentCommitHash() throws IOException {
        ObjectId headCommit = gitRepository.resolve("HEAD");
        if (headCommit == null) {
            return null;
        }
        return headCommit.getName();
    }

    /**
     * Returns the repository's origin URL
     *
     * @return the origin URL as a String, or null if not configured
     */
    /**
     * Returns the repository's origin URL
     *
     * @return the origin URL as a String, or null if not configured
     */
    public String getOriginUrl() {
        return gitRepository.getConfig().getString("remote", "origin", "url");
    }

    public String getRepoUrl() throws IOException {
        String repoUrl;
        String originUrl = getOriginUrl();
        if (originUrl != null && originUrl.startsWith("git@")) {
            originUrl = originUrl.replace(":", "/").replace("git@", "https://");
        }

        if (originUrl == null) {
            return "";
        }

        // Only web URLs may be embedded in generated reports as source links;
        // anything else (javascript:, data:, file:, ...) is rejected outright.
        if (!originUrl.startsWith("https://") && !originUrl.startsWith("http://")) {
            return "";
        }

        repoUrl = originUrl.replace(".git", "");

        if (repoUrl.contains("gitlab")) {
            repoUrl = repoUrl + "/-/blob/" + getCurrentCommitHash() + "/";
        } else if (repoUrl.contains("bitbucket")) {
            repoUrl = repoUrl + "/src/" + getCurrentCommitHash() + "/";
        } else {
            repoUrl = repoUrl + "/blob/" + getCurrentCommitHash() + "/";
        }

        // Keep only RFC 3986 URL characters: drops every char that is markup- or
        // JS-significant outside a URL (space, ", ', <, >, `, {, }, \), so the value
        // cannot break out of an attribute or a <script> template literal downstream.
        return repoUrl.replaceAll("[^A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]", "");
    }

    // log --follow implementation may be worth adopting in the future
    // https://github.com/spearce/jgit/blob/master/org.eclipse.jgit.pgm/src/org/eclipse/jgit/pgm/RevWalkTextBuiltin.java

    /**
     * Returns the number of commits and earliest commit for a given path
     * TODO: Move to a different class???
     *
     * @param path
     * @return a LogInfo object
     * @throws GitAPIException
     */
    public ScmLogInfo fileLog(String path) throws GitAPIException, IOException {
        ObjectId branchId = gitRepository.resolve("HEAD");
        CommitWalkStats stats =
                walkCommits(git.log().add(branchId).addPath(path).call());

        if (stats.commitCount == 0) {
            return new ScmLogInfo(path, null, stats.earliestCommit, stats.earliestCommit, stats.commitCount);
        }

        return new ScmLogInfo(path, null, stats.earliestCommit, stats.mostRecentCommit, stats.commitCount);
    }

    /**
     * Counts commits over the given walk.  A missing Git object (e.g. in a shallow or
     * partial clone) must not fail the whole walk; the walk is truncated and the
     * commits read so far are returned instead.
     */
    private static CommitWalkStats walkCommits(Iterable<RevCommit> revCommits) {
        CommitWalkStats stats = new CommitWalkStats();

        try {
            for (RevCommit revCommit : revCommits) {
                int commitTime = revCommit.getCommitTime();
                if (stats.commitCount == 0) {
                    stats.mostRecentCommit = commitTime;
                }
                if (commitTime < stats.earliestCommit) {
                    stats.earliestCommit = commitTime;
                }
                stats.commitCount++;
            }
        } catch (RevWalkException e) {
            // JGit wraps checked exceptions thrown mid-walk in a RevWalkException.
            if (isCausedByMissingObject(e)) {
                log.warn(
                        "Missing Git object while reading history (shallow or partial clone?); "
                                + "reporting the {} commit(s) that could be read. Cause: {}",
                        stats.commitCount,
                        e.getMessage());
            } else {
                throw e;
            }
        }

        return stats;
    }

    private static boolean isCausedByMissingObject(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof MissingObjectException) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private static class CommitWalkStats {
        int commitCount = 0;
        int earliestCommit = Integer.MAX_VALUE;
        int mostRecentCommit = 0;
    }

    // based on https://stackoverflow.com/questions/27361538/how-to-show-changes-between-commits-with-jgit
    public TreeMap<Integer, Integer> captureChangeCountByCommitTimestamp() throws IOException, GitAPIException {

        TreeMap<Integer, Integer> changesByCommitTimestamp = new TreeMap<>();

        ObjectId branchId = gitRepository.resolve("HEAD");
        List<RevCommit> commitList = new ArrayList<>();
        git.log().add(branchId).call().forEach(commitList::add);

        if (commitList.isEmpty()) {
            return changesByCommitTimestamp;
        }

        // Handle first / initial commit
        changesByCommitTimestamp.putAll(walkFirstCommit(commitList.get(commitList.size() - 1)));

        if (commitList.size() < 2) {
            return changesByCommitTimestamp;
        }

        // Process adjacent commit pairs in parallel; each pair is independent
        ConcurrentMap<Integer, Integer> concurrentResults = new ConcurrentHashMap<>();
        IntStream.range(0, commitList.size() - 1).parallel().forEach(i -> {
            RevCommit newer = commitList.get(i);
            RevCommit older = commitList.get(i + 1);
            try {
                int count = 0;
                for (DiffEntry entry : getDiffEntries(newer, older)) {
                    if (entry.getNewPath().endsWith(JAVA_FILE_TYPE)
                            || entry.getOldPath().endsWith(JAVA_FILE_TYPE)
                            || entry.getNewPath().endsWith(KOTLIN_FILE_TYPE)
                            || entry.getOldPath().endsWith(KOTLIN_FILE_TYPE)) {
                        count++;
                    }
                }
                if (count > 0) {
                    concurrentResults.put(newer.getCommitTime(), count);
                }
            } catch (IOException e) {
                log.error("Error getting diff entries: {}", e.getMessage());
            }
        });

        changesByCommitTimestamp.putAll(concurrentResults);
        return changesByCommitTimestamp;
    }

    private List<DiffEntry> getDiffEntries(RevCommit newCommit, RevCommit oldCommit) throws IOException {
        try (ObjectReader reader = gitRepository.newObjectReader();
                DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE)) {
            df.setRepository(gitRepository);
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, newCommit.getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, oldCommit.getTree());
            return df.scan(oldTreeIter, newTreeIter);
        }
    }

    Map<Integer, Integer> walkFirstCommit(RevCommit firstCommit) throws IOException {
        Map<Integer, Integer> changesByCommitTimestamp = new TreeMap<>();
        int firstCommitCount = 0;
        ObjectId treeId = firstCommit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(gitRepository)) {
            treeWalk.setRecursive(false);
            treeWalk.reset(treeId);
            while (treeWalk.next()) {
                if (treeWalk.isSubtree()) {
                    treeWalk.enterSubtree();
                } else {
                    if (treeWalk.getPathString().endsWith(JAVA_FILE_TYPE)
                            || treeWalk.getPathString().endsWith(KOTLIN_FILE_TYPE)) {
                        firstCommitCount++;
                    }
                }
            }
        }

        if (firstCommitCount > 0) {
            changesByCommitTimestamp.put(firstCommit.getCommitTime(), firstCommitCount);
        }

        return changesByCommitTimestamp;
    }
}
