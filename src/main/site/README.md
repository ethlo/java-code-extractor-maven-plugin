### source-extractor-maven-plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.ethlo.documentation/source-extractor-maven-plugin.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.ethlo.time%22%20a%3A%22itu%22)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](LICENSE)

An opinionated little tool to extract code samples for inclusion in HTML or markdown to avoid maintaining example code separately from tests.

An example can be seen here: https://github.com/ethlo/itu/?tab=readme-ov-file#parsing

Standing on the shoulders of these awesome projects:
* https://github.com/javaparser/javaparser
* https://github.com/PebbleTemplates/pebble

### How it works
You define the plugin to extract methods from classes in a given package. These are processed and then rendered with the specified template,  and are available as a variable name matching the defined `<source>` name.
This variable can then be used in maven resource interpolation to be included in a readme file, for example.

### Usage

We need to set up the plugin to extract the relevant source code. I recommend a package with samples you want to showcase, like `src/test/java/mysamples`.

The example below stores a `README.md` _template_ in `src/site`.

The `pom.xml` resources section (must be filtered) so that you can include snippets rendered.
```xml
<project>
    <build>
        <plugins>
            <plugin>
                <groupId>com.ethlo.documentation</groupId>
                <artifactId>source-extractor-maven-plugin</artifactId>
                <version>${project.version}</version>
                <configuration>
                    <template>src/main/site/github.template.md</template>
                    <sources>
                        <source>src/test/java/samples</source>
                    </sources>
                </configuration>
                <executions>
                    <execution>
                        <phase>initialize</phase>
                        <goals>
                            <goal>extract</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
            </resource>
            <resource>
                <filtering>true</filtering>
                <directory>\${project.basedir}/src/main/site</directory>
                <includes>
                    <include>README.md</include>
                </includes>
                <targetPath>\${project.basedir}</targetPath>
            </resource>
        </resources>
    </build>
</project>
```
### Extracted samples

${src/test/java/samples}


