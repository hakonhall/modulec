# modulec

`modulec` is a program and API for compiling a Java 9+ module into a modular JAR file. Example usage:

```
$ modulec src
```

will create a module JAR in `target/MODULE.jar`, where `MODULE` is the module name defined by `src/module-info.java`.

To install `modulec` to ~/bin/modulec:

```
make
make install
```

## Variants

`modulec` is small enough that it can be used to test the performance characteristics of 3 different launching mechanisms of `javac` and `jar`:

1. Run `javac` and `jar` through a shell script that has the same features as (2) and (3).
2. Run `~/bin/modulec`: The symlink installed by above installation, that is a symlink to a shell program that executes a Java program that invokes the equivalent `javac` and `jar` commands of (1) through a Java API.
3. Run the java command in `~/bin/modulec` 
4. Run `bin/modulec-shebang`: a single-file source-code Java program that utilizes shebang, see [JEP 330](https://openjdk.java.net/jeps/330) for details on this mechanism.


The average of 20 back-to-back compilation of a simple project that should be a no-op, finds (1) takes 0.24s, (2) 0.27s, (3) 0.27s, and (4) 0.59s.  For some reason it is faster to spawn 2 JVMs in (1) than one JVM invoking presumably the same APIs.  One might suspect that those APIs actually spawns processes, but that cannot be the case as the APIs still worked after I moved away `javac`.
