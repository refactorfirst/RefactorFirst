# RefactorFirst

This tool for Java codebases will help you identify the God Classes and classes with High Coupling you should refactor first.
It runs PMD's God Class Rule and Coupling Between Objects rule and scans your Git repository history.

The graphs generated in the report will look similar to this one:
![image info](./RefactorFirst_Sample_Report.png)

## Please Note: Java 11 is now required to run RefactorFirst
The change to require Java 11 is needed to address vulnerability CVE-2023-4759 in JGit

## There are several ways to run the analysis on your codebase:

### From The Command Line
Run the following command from the root of your project (the source code does not need to be built):

```bash
mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.5.0:report
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
            <version>0.5.0</version>       
            <!-- optional -->
            <configuration>
                <showDetails>true</showDetails>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

### As a Maven Report
Add the following to your project in the reports section.
```xml
<reporting>
    <plugins>
        ...
        <plugin>
            <groupId>org.hjug.refactorfirst.plugin</groupId>
            <artifactId>refactor-first-maven-plugin</artifactId>
            <version>0.5.0</version>       
        </plugin>
        ...
    </plugins>
</reporting>
```

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

### As an HTML Report
```bash
mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.5.0:htmlReport
```
View the report at ```target/site/refactor-first-report.html```

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
mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.5.0:htmlReport
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
* Identify class cycles and prioritize them.
* Add a Gradle plugin.
* Incorporate Unit Test coverage metrics to quickly identify the safety of refactoring a God class.
* Incorporate bug counts per God class to the Impact (Y-Axis) calculation.
* Incorporate more disharmonies from Object Oriented Metrics In Practice (Lanza and Marinescu, 2004).

## Note:
If you are a user of Version 0.1.0 or 0.1.1, you may notice that the list of God classes found by the plugin has changed.  This is due to changes in PMD.

# Thank You!  Enjoy!
