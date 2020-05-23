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

## Variants and performance of Java compilation and packaging

`modulec` is small enough that it can be used to test the performance characteristics of 3 different launching mechanisms of `javac` and `jar`:

1. Run `javac` and `jar` through a shell script that has the same features as (2) and (3).
2. Run `~/bin/modulec`: The symlink installed by above installation, that is a symlink to a shell program that executes a Java program that invokes the equivalent `javac` and `jar` commands of (1) through a Java API.
3. Run the java command in `~/bin/modulec` to test the overhead in bash.
4. Run `bin/modulec-shebang`: a single-file source-code Java program that utilizes shebang, see [JEP 330](https://openjdk.java.net/jeps/330) for details on this mechanism.

The average of 10 back-to-back compilation of a simple project that should be a no-op, finds (1) takes 0.24s, (2) 0.27s, (3) 0.27s, and (4) 0.59s.  For some reason it is faster to spawn 2 JVMs in (1) than one JVM invoking presumably the same APIs.  One might suspect that those APIs actually spawns processes, but that cannot be the case as the APIs still worked after I moved away `javac`.

Instead of having the shell trigger the execution 10 times, I implemented a Java loop over `ModuleCompiler.make()` and the cumulative time was 0.32s.

5. Java-loop over `ModuleCompiler.make()` for multiple runs:  Is equivalent to (2) with only 1 run.

In fact running it 1000 times only took 1.7 seconds. Could this be a trickery of Java - that it somehow skips compilation and/or packaging?  No, because it would have no way of knowing nothing changed on disk (unless it set up a file watcher, and even then it could lead to bugs due to timing issues).

To gain more insight I implemented removing the entire output directory after each run, and take the average over 100 runs.  For (1)-(4) the output directory was removed with `rm -r`.  The average was 0.49s for (1), 0.39s for (2), 0.39s for (3), 0.63s for (4), and 0.017s for (5).  I have double-checked I have made no power-of-10 mistake on (5).  If the same is done only over 10 runs, I get 0.47s for (1), 0.38s for (2), 0.39s for (3), 0.62s for (4), and 0.065s for (5).  So these are constants except for (5).  With 1000 runs, it is 0.0081s per compile and delete combo, and with 10k it's 0.0058s.  So a Java implementation looks like it converges towards about 5ms.

This speedup comes from JIT compilation improving the performance the more chance it has on optimizing the involved java code.  The JIT compilation overhead may or may not explain why it is more costly to run JVM twice rather than once in the original findings: perhaps the JIT compiler slowes down execution compared with a short invocation.  It could also be that the `javac` and `jar` tools have hand-crafted JIT compilation settings to avoid unnecessary JIT compilation overhead when the process will dies shortly anyways.
