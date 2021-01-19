# Disharmony Ranker

This tool for Java codebases will help you identify the God Classes you should refactor first.
The graph generated in the report will look similar to this one:
![image info](./RefactorFirst_Sample_Report.png)

## There are several ways to run the analysis on your codebase:

### From The Command Line
Run the following command from the root of your project (the source code does not need to be built):

```bash
mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.1.0:report
```

### As Part of a Build
Add the following to your project in the build section:
```xml
<build>
    <plugins>
        ...
        <plugin>
            <groupId>org.hjug.refactorfirst.plugin</groupId>
            <artifactId>refactor-first-maven-plugin</artifactId>
            <version>0.1.0</version>       
        </plugin>
        ...
    </plugins>
</build>
```

### As a Report
Add the following to your project in the reports section:
```xml
<reporting>
    <plugins>
        ...
        <plugin>
            <groupId>org.hjug.refactorfirst.plugin</groupId>
            <artifactId>refactor-first-maven-plugin</artifactId>
            <version>0.1.0</version>       
        </plugin>
        ...
    </plugins>
</reporting>
```

Once the plugin is finished executing (it may take a while for large / old codebases), open the file **target/site/refactor-first-report.html** in the root of the project.  It will contain a graph similar to the one above, and a table that lists God classes as well as the recommended order they should be refactored in.  The classes you should refactor first are at the top.

This plugin will work on both single module and multi-module Maven projects.
 
This tool is based on the paper **[Prioritizing Design Debt Investment Opportunities](https://dl.acm.org/doi/10.1145/1985362.1985372)** by Nico Zazworka, Carolyn Seaman, and Forrest Shull

There is still much to be done.  Your feedback would be greatly appreciated!

## Limitations
* This plugin only works with Java 8 projects at this time

## Future Plans
* Improve logging.
* Move from JUnit 4 to Junit 5.  Junit 5 is lacking the support for temporary files that JUnit 4 provides.
* Use Maven's Guice to autowire dependencies.
* Incorporate more disharmonies from Object Oriented Metrics In Practice (Lanza and Marinescu, 2004).
* Support Java 11


