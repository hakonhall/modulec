# modulec

`modulec` is a program for compiling a Java 9+ module into a modular JAR file.

I have tested whether lauching a single JVM is cheaper than launching `javac` then `jar`:  On my test machine,
launching `modulec` as a single-file source-code program using shebang costs a few 100ms more than making it
just invoking `javac` and `jar`.  However launching `java` on the `modulec` class file beats that again by 100ms,
so the problem with the shebang file has all to do with the JVM needing to compile the file before executing it.
