# RefactorFirst

This tool for Java codebases will help you identify what you should refactor first:
- God Classes
- Highly Coupled classes
- Class Cycles (with cycle images!)

It scans your Git repository generates a single page application by runing:
- Cycle analysis on your source code using the [OpenRewrite](https://github.com/openrewrite/rewrite) Java parser and [JGraphT](https://jgrapht.org/)
- What-if analysis to identify the most optimal relationships in a class cycle to remove
- PMD's God Class Rule
- PMD's Coupling Between Objects

Code map viewers are powered by [3D Force Graph](https://vasturiano.github.io/3d-force-graph), [sigma.js](https://www.sigmajs.org/), and [GraphViz DOT](https://graphviz.org/docs/layouts/dot/)
<br>If there are more than 4000 classes + relationships, a simplified 3D viewer will be used to avoid slowdowns.  Features will be toggleable in the 3D UI in a future release.

Take a look at the [Spring Petclinic REST project sample report](https://rawcdn.githack.com/refactorfirst/RefactorFirst/035e141f7a42920a32d96f74e819ad370fece5e7/spring-petclinic-rest-report.html)!

The graphs generated in the report will look similar to this one:
![image info](./RefactorFirst_Sample_Report.png)

## Please Note: Java 11 (or newer) required to run RefactorFirst
**Java 21 codebase analysis is supported!**
The change to require Java 11 is needed to address vulnerability CVE-2023-4759 in JGit 
Please use a recent JDK release of the Java version you are using.  
If you use an old JDK release of your chosen Java version, you may encounter issues during analysis.


## There are several ways to run the analysis on your codebase:

### From The Command Line As an HTML Report
Run the following command from the root of your project (the source code does not need to be built):

```bash
mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.7.0:htmlReport
```
View the report at ```target/site/refactor-first-report.html```

### [As Part of GitHub Actions Output](https://github.blog/news-insights/product-news/supercharging-github-actions-with-job-summaries/)
This will generate a simplified HTML report (no graphs or images) as the output of a GitHub Action step
```bash
mvn -B clean test \
org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.7.0:simpleHtmlReport \
&& echo "$(cat target/site/refactor-first-report.html)" >> $GITHUB_STEP_SUMMARY
```

### As Part of a Build
Add the following to your project in the build section.  **showDetails** will show God Class metrics and rankings in the generated table.
```xml
<build>
    <plugins>
        ...
        <plugin>
            <groupId>org.hjug.refactorfirst.plugin</groupId>
            <artifactId>refactor-first-maven-plugin</artifactId>
            <version>0.7.0</version>       
            <!-- optional -->
            <configuration>
                <showDetails>false</showDetails>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

### As a Maven Report
Add the following to your project in the reports section.   
A RefactorFirst report will show up in the site report when you run ```mvn site```
```xml
<reporting>
    <plugins>
        ...
        <plugin>
            <groupId>org.hjug.refactorfirst.plugin</groupId>
            <artifactId>refactor-first-maven-plugin</artifactId>
            <version>0.7.0</version>       
        </plugin>
        ...
    </plugins>
</reporting>
```

## Configuraiton Options
Care has been taken to use sensible defaults, though if you wish to override these defaults you can specify the following parameters.
Specify with -D if running on the command line.  e.g. ```-DbackEdgeAnalysisCount=0 `DanalyzeCycles=false``` or in the configuration section (as in the above examples) if including in a Maven build.

|Option|Action|Default|
|------|------|-------|
|showDetails|Shows God Class metrics|false|
|backEdgeAnalysisCount|Number of back edges in a cycle to analyze.  <br>If total number of back edges is greater than the value specified, it analyzes the number of minimum weight edges specified.<br>**If 0 is specified, all back edges will be analyzed**|50|
|analyzeCycles|Analyzes the 10 largest cycles (will be configurable in the future)|true|
|minifyHtml|Minifies the generated HTML report.  Only available on ```htmlReport``` and ```simpleHtmlReport``` goals.  May cause issues with large reports.|false|
|excludeTests|Exclude test classes from analysis|true|
|testSrcDirectory|Excludes classes containing this pattern from analysis|```src/test``` and ```src/test```|
|projectName|The name of your project to be displayed on the report|Your Maven project name|
|projectVersion|The version of your project to be displayed on the report|Your Maven project version|
|outputDirectory|The location the project report will be written|```${projectDir}/target/site/refactor-first-report.html```


### Seeing Errors?

If you see an error similar to
```
 Execution default-site of goal org.apache.maven.plugins:maven-site-plugin:3.3:site failed: A required class was missing while executing org.apache.maven.plugins:maven-site-plugin:3.3:site: org/apache/maven/doxia/siterenderer/DocumentContent
```
you will need to add the following to your pom.xml:
```xml
  <build>
    <plugins>        
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-site-plugin</artifactId>
        <version>3.12.1</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-project-info-reports-plugin</artifactId>
        <version>3.4.5</version>
      </plugin>
    </plugins>
  </build>
```


## But I'm using Gradle / my project layout isn't typical!
I would like to create a Gradle plugin and (possibly) support non-conventional projects in the future, but in the meantime you can create a dummy POM file in the same directory as your .git directory:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
 
  <groupId>com.mycompany.app</groupId>
  <artifactId>my-app</artifactId>
  <version>1.0-SNAPSHOT</version>
</project>
```
and then (assuming Maven is installed) run

```bash
mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.7.0:htmlReport
```

## Viewing the Report
View the report at ```target/site/refactor-first-report.html```   
Once the plugin finishes executing (it may take a while for a large / old codebase), open the file **target/site/refactor-first-report.html** in the root of the project.  It will contain a graph similar to the one above, and a table that lists God classes in the recommended order that they should be refactored.  The classes in the top left of the graph are the easiest to refactor while also having the biggest positive impact to team productivity.  
If highly coupled classes are detected, a graph and table listing Highly Coupled Classes in will be generated.

## I have the report.  Now What???
Work with your Product Owner to prioritize the technical debt that has been identified.  It may help to explain it as hidden negative value that is slowing team porductivity.  
If you have IntelliJ Ultimate, you can install the [Method Reference Diagram](https://plugins.jetbrains.com/plugin/7996-java-method-reference-diagram) plugin to help you determine how the identified God classes and Highly Coupled classes can be refactored.


## Additional Details
This plugin will work on both single module and multi-module Maven projects that have a typical Maven project layout.
 
This tool is based on the paper **[Prioritizing Design Debt Investment Opportunities](https://dl.acm.org/doi/10.1145/1985362.1985372)** by Nico Zazworka, Carolyn Seaman, and Forrest Shull.  The presentation based on the paper is available at https://resources.sei.cmu.edu/asset_files/Presentation/2011_017_001_516911.pdf 

## Limitations
* My time.  This is a passion project and is developed in my spare time.

## Feedback and Collaboration Welcome
There is still much to be done.  Your feedback and collaboration would be greatly appreciated in the form of feature requests, bug submissions, and PRs.  
If you find this plugin useful, please star this repository and share with your friends & colleagues and on social media.

## Future Plans
* Improve class cycle analysis (only field member types and method signature types are currently supported).
* Add a Gradle plugin.
* Incorporate Unit Test coverage metrics to quickly identify the safety of refactoring classes.
* Incorporate bug counts per class to the Impact (Y-Axis) calculation.
* Incorporate more disharmonies from Object Oriented Metrics In Practice (Lanza and Marinescu, 2004).

## Note:
If you are a user of Version 0.1.0 or 0.1.1, you may notice that the list of God classes found by the plugin has changed.  This is due to changes in PMD.

# Thank You!  Enjoy!
