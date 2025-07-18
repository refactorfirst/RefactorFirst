package org.hjug.git;

import java.io.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.NullOutputStream;

@Slf4j
public class GitLogReader implements AutoCloseable {

    static final String JAVA_FILE_TYPE = ".java";

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

    public File getGitDir(File basedir) {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(basedir);
        return repositoryBuilder.getGitDir();
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
        Iterable<RevCommit> revCommits = git.log().add(branchId).addPath(path).call();

        int commitCount = 0;
        int earliestCommit = Integer.MAX_VALUE;
        for (RevCommit revCommit : revCommits) {
            if (revCommit.getCommitTime() < earliestCommit) {
                earliestCommit = revCommit.getCommitTime();
            }
            commitCount++;
        }

        // based on https://stackoverflow.com/a/59274329/346247
        int mostRecentCommit = git.log()
                .add(branchId)
                .addPath(path)
                .setMaxCount(1)
                .call()
                .iterator()
                .next()
                .getCommitTime();

        return new ScmLogInfo(path, earliestCommit, mostRecentCommit, commitCount);
    }

    // based on https://stackoverflow.com/questions/27361538/how-to-show-changes-between-commits-with-jgit
    public TreeMap<Integer, Integer> captureChangeCountByCommitTimestamp() throws IOException, GitAPIException {

        TreeMap<Integer, Integer> changesByCommitTimestamp = new TreeMap<>();

        ObjectId branchId = gitRepository.resolve("HEAD");
        Iterable<RevCommit> commits = git.log().add(branchId).call();

        RevCommit newCommit = null;

        for (Iterator<RevCommit> iterator = commits.iterator(); iterator.hasNext(); ) {
            RevCommit oldCommit = iterator.next();

            int count = 0;
            if (null == newCommit) {
                newCommit = oldCommit;
                continue;
            }

            for (DiffEntry entry : getDiffEntries(newCommit, oldCommit)) {
                if (entry.getNewPath().endsWith(JAVA_FILE_TYPE)
                        || entry.getOldPath().endsWith(JAVA_FILE_TYPE)) {
                    count++;
                }
            }

            if (count > 0) {
                changesByCommitTimestamp.put(newCommit.getCommitTime(), count);
            }

            // Handle first / initial commit
            if (!iterator.hasNext()) {
                changesByCommitTimestamp.putAll(walkFirstCommit(oldCommit));
            }

            newCommit = oldCommit;
        }

        return changesByCommitTimestamp;
    }

    private List<DiffEntry> getDiffEntries(RevCommit newCommit, RevCommit oldCommit) throws IOException {
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            ObjectId oldTree = git.getRepository().resolve(newCommit.getTree().name());
            oldTreeIter.reset(reader, oldTree);
            ObjectId newTree = git.getRepository().resolve(oldCommit.getTree().name());
            newTreeIter.reset(reader, newTree);
        }

        DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE);
        df.setRepository(git.getRepository());
        return df.scan(oldTreeIter, newTreeIter);
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
                    if (treeWalk.getPathString().endsWith(JAVA_FILE_TYPE)) {
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
