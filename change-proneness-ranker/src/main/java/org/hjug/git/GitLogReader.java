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
public class GitLogReader implements RepositoryLogReader {

    static final String JAVA_FILE_TYPE = ".java";

    // Based on
    // https://github.com/Cosium/git-code-format-maven-plugin/blob/master/src/main/java/com/cosium/code/format/AbstractMavenGitCodeFormatMojo.java
    // MIT License
    // Move to a provider?
    @Override
    public Repository gitRepository(File basedir) throws IOException {
        Repository gitRepository;
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(basedir);
        String gitIndexFileEnvVariable = System.getenv("GIT_INDEX_FILE");
        if (Objects.nonNull(gitIndexFileEnvVariable)
                && !gitIndexFileEnvVariable.trim().isEmpty()) {
            log.debug("Setting Index File based on Env Variable GIT_INDEX_FILE {}", gitIndexFileEnvVariable);
            repositoryBuilder = repositoryBuilder.setIndexFile(new File(gitIndexFileEnvVariable));
        }
        gitRepository = repositoryBuilder.build();

        return gitRepository;
    }

    public File getGitDir(File basedir) {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(basedir);
        return repositoryBuilder.getGitDir();
    }

    // https://stackoverflow.com/a/19950970/346247
    // and
    // https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/api/ReadFileFromCommit.java
    @Override
    public Map<String, ByteArrayOutputStream> listRepositoryContentsAtHEAD(Repository repository) throws IOException {
        Ref head = repository.exactRef("HEAD");
        // a RevWalk allows us to walk over commits based on some filtering that is defined
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(head.getObjectId());
        RevTree tree = commit.getTree();

        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(false);

        // TODO: extract rest of this method to test it
        Map<String, ByteArrayOutputStream> fileContentsCollection = new HashMap<>();
        while (treeWalk.next()) {
            if (treeWalk.isSubtree()) {
                treeWalk.enterSubtree();
            } else {
                if (treeWalk.getPathString().endsWith(JAVA_FILE_TYPE)) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    loader.copyTo(outputStream);
                    fileContentsCollection.put(treeWalk.getPathString(), outputStream);
                }
            }
        }
        return fileContentsCollection;
    }

    // log --follow implementation may be worth adopting in the future
    // https://github.com/spearce/jgit/blob/master/org.eclipse.jgit.pgm/src/org/eclipse/jgit/pgm/RevWalkTextBuiltin.java

    /**
     * Returns the number of commits and earliest commit for a given path
     * TODO: Move to a different class???
     *
     * @param repository
     * @param path
     * @return a LogInfo object
     * @throws GitAPIException
     */
    @Override
    public ScmLogInfo fileLog(Repository repository, String path) throws GitAPIException, IOException {
        Git git = new Git(repository);
        ObjectId branchId = repository.resolve("HEAD");
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
        int mostRecentCommit = new Git(repository)
                .log()
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
    @Override
    public TreeMap<Integer, Integer> captureChangeCountByCommitTimestamp(Repository repository)
            throws IOException, GitAPIException {

        TreeMap<Integer, Integer> changesByCommitTimestamp = new TreeMap<>();

        try (Git git = new Git(repository)) {
            ObjectId branchId = repository.resolve("HEAD");
            Iterable<RevCommit> commits = git.log().add(branchId).call();

            RevCommit newCommit = null;

            for (Iterator<RevCommit> iterator = commits.iterator(); iterator.hasNext(); ) {
                RevCommit oldCommit = iterator.next();

                int count = 0;
                if (null == newCommit) {
                    newCommit = oldCommit;
                    continue;
                }

                for (DiffEntry entry : getDiffEntries(git, newCommit, oldCommit)) {
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
                    changesByCommitTimestamp.putAll(walkFirstCommit(repository, oldCommit));
                }

                newCommit = oldCommit;
            }
        }
        return changesByCommitTimestamp;
    }

    private List<DiffEntry> getDiffEntries(Git git, RevCommit newCommit, RevCommit oldCommit) throws IOException {
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

    Map<Integer, Integer> walkFirstCommit(Repository repository, RevCommit firstCommit) throws IOException {
        Map<Integer, Integer> changesByCommitTimestamp = new TreeMap<>();
        int firstCommitCount = 0;
        ObjectId treeId = firstCommit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
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
