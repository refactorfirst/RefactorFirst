package org.hjug.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

public interface RepositoryLogReader {

    Repository gitRepository(File basedir) throws IOException;

    Map<String, ByteArrayOutputStream> listRepositoryContentsAtHEAD(Repository repository) throws IOException;

    ScmLogInfo fileLog(Repository repository, String path) throws GitAPIException, IOException;

    TreeMap<Integer, Integer> captureChangeCountByCommitTimestamp(Repository repository)
            throws IOException, GitAPIException;
}
