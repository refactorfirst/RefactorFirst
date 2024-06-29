package org.hjug.cbc;

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

class CostBenefitCalculatorTest {

    @TempDir
    public File tempFolder;

    private String faceletsPath = "org/apache/myfaces/tobago/facelets/";
    private String hudsonPath = "hudson/model/";
    private Git git;
    private Repository repository;

    @BeforeEach
    public void setUp() throws GitAPIException {
        git = Git.init().setDirectory(tempFolder).call();
        repository = git.getRepository();
        new File(tempFolder.getPath() + "/" + faceletsPath).mkdirs();
        new File(tempFolder.getPath() + "/" + hudsonPath).mkdirs();
    }

    @AfterEach
    public void tearDown() {
        repository.close();
    }

    @Test
    void testCBOViolation() throws IOException, GitAPIException, InterruptedException {
        // Has CBO violation
        String user = "User.java";
        InputStream userResourceAsStream = getClass().getClassLoader().getResourceAsStream(hudsonPath + user);
        writeFile(hudsonPath + user, convertInputStreamToString(userResourceAsStream));

        git.add().addFilepattern(".").call();
        RevCommit firstCommit = git.commit().setMessage("message").call();

        CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
        costBenefitCalculator.runPmdAnalysis(git.getRepository().getDirectory().getParent());
        List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateCBOCostBenefitValues(
                git.getRepository().getDirectory().getPath());

        Assertions.assertFalse(disharmonies.isEmpty());
    }

    @Test
    void testCostBenefitCalculation() throws IOException, GitAPIException, InterruptedException {

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(faceletsPath + attributeHandler);
        writeFile(faceletsPath + attributeHandler, convertInputStreamToString(resourceAsStream));

        git.add().addFilepattern(".").call();
        RevCommit firstCommit = git.commit().setMessage("message").call();

        // Sleeping for one second to guarantee commits have different time stamps
        Thread.sleep(1000);

        // write contents of updated file to original file
        InputStream resourceAsStream2 =
                getClass().getClassLoader().getResourceAsStream(faceletsPath + "AttributeHandler2.java");
        writeFile(faceletsPath + attributeHandler, convertInputStreamToString(resourceAsStream2));

        InputStream resourceAsStream3 =
                getClass().getClassLoader().getResourceAsStream(faceletsPath + "AttributeHandlerAndSorter.java");
        writeFile(faceletsPath + "AttributeHandlerAndSorter.java", convertInputStreamToString(resourceAsStream3));

        git.add().addFilepattern(".").call();
        RevCommit secondCommit = git.commit().setMessage("message").call();

        CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
        costBenefitCalculator.runPmdAnalysis(git.getRepository().getDirectory().getParent());
        List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues(
                git.getRepository().getDirectory().getPath());

        Assertions.assertEquals(1, disharmonies.get(0).getRawPriority().intValue());
        Assertions.assertEquals(1, disharmonies.get(1).getRawPriority().intValue());

        Assertions.assertEquals(1, disharmonies.get(0).getPriority().intValue());
        Assertions.assertEquals(2, disharmonies.get(1).getPriority().intValue());
    }

    private void writeFile(String name, String content) throws IOException {
        // Files.writeString(Path.of(git.getRepository().getWorkTree().getPath()), content);
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
