Pipes
=======

Light-weight data processing pipelines for Java.

Pipes is provided under the [Apache License Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Example
-------

This example uses Pipes to read an arbitrary set of text files and output their contents line-by-line.

```java

public static void main(final String... args) throws StreamException
{
    // A pipe to convert arguments to paths
    final Pipe<String[],Path> argsToPaths = (i,o) -> {
        for(String arg : i) o.accept(Paths.get(arg));
    };
    // A pipe to open paths as byte streams
    final Pipe<Path, InputStream> pathToStream = (i,o) -> {
    try {
      o.accept(Files.newInputStream(i, StandardOpenOption.READ));
    } catch (final IOException e) {
      throw new PipeException(e);
    }
    };
    // A pipe to read lines of text from byte streams
    final Pipe<InputStream, String> streamToLines = (i,o) -> {
    try(final BufferedReader reader = new BufferedReader(new InputStreamReader(i, StandardCharsets.UTF_8))) {
      for(String line = reader.readLine(); line != null; line = reader.readLine()){
        o.accept(line);
      }
    } catch (final IOException e) {
      throw new PipeException(e);
    }
    };
    // A predicate to filter out any blank lines or lines starting with '#'.
    final PipePredicate<String> nonComments = line -> line != null && !line.isEmpty() && !line.matches("\\s*(#.*)?");

    // Build the pipeline
    final Pipe<String, String> filteredLines = argToPath.append(pathToStream)
                                                      .append(streamToLines)
                                                      .filter(nonComments);

    // Consume the pipeline by printing to standard out.
    filteredLines.flush(args, line -> System.out.println(line));
}

```

<!--- Cloudbees repository not available anymore

Maven
-----

Include the following in your pom.xml to start using Streams.

```xml
<dependencies>
    <dependency>
        <groupId>au.id.ajlane.common</groupId>
        <artifactId>pipes</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>ajlane-snapshots</id>
        <layout>default</layout>
        <url>http://repository-ajlane.forge.cloudbees.com/snapshot/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

-->