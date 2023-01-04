# JUnit 5

The lib directory contains the downloaded JAR artifacts.  The mod directory
contains the same artifacts, but made into valid hybrid modular JARs.

## JUnit 5 hybrid modular JARs

The JARs downloaded from Maven Central had two problems: 1. Some lacked a
module version, while 2. some lacked version on their dependencies.

By starting at the leaves of the dependency tree between the JARs, a
module-info.java was made and compiled at the version of the module.  The JAR
was then updated with the module-info.class.  This fixed both (1) and (2) for
the leaves.  Disregarding those JARs, the next leaves where fixed in the same
way, and so on.  The net result was the hybrid modular JARs in the mod
directory.

To see the difference, use module-info.

## Dependency tree

In the following, all dependencies on java modules are ignored, and redundant
dependencies have been removed.

org.apiguardian.api@1.1.2
org.junit.platform.commons@1.9.1
    requires transitive org.apiguardian.api@1.1.2;
org.opentest4j@1.2.0
org.junit.platform.engine@1.9.1
    requires transitive org.junit.platform.commons@1.9.1;
    requires transitive org.opentest4j@1.2.0;
org.junit.platform.launcher@1.9.1
    requires transitive org.junit.platform.engine@1.9.1;
org.junit.jupiter.api@5.9.1
    requires transitive org.junit.platform.commons@1.9.1;
    requires transitive org.opentest4j@1.2.0;
org.junit.jupiter.engine@5.9.1
    requires org.junit.jupiter.api@5.9.1;
    requires org.junit.platform.engine@1.9.1;
