package no.ion.modulec.compiler;

import javax.lang.model.SourceVersion;
import java.util.Objects;

/**
 * Represents a Java SE release, e.g. 11.
 */
public class Release {
    private final int release;
    private final SourceVersion sourceVersion;

    /** Returns a release matching the JRE. */
    public static Release ofJre() {
        return fromSourceVersion(SourceVersion.latest());
    }

    /** Returns a release based on the Java SE feature release counter, e.g. 9, 11, and 17.  Formerly major version. */
    public static Release fromFeatureReleaseCounter(int release) throws IllegalArgumentException {
        final SourceVersion sourceVersion;
        try {
            sourceVersion = SourceVersion.valueOf("RELEASE_" + release);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown Java release: " + release);
        }
        return new Release(release, sourceVersion);
    }

    /** Returns a release based on the source version. null implies the latest source version. */
    public static Release fromSourceVersion(SourceVersion sourceVersion) {
        if (sourceVersion == null)
            sourceVersion = SourceVersion.latest();
        return new Release(sourceVersion.ordinal(), sourceVersion);
    }

    public Release(int release, SourceVersion sourceVersion) {
        // We should actually verify the release is one of com.sun.tools.javac.platform.PlatformProvider.getSupportedPlatformNames().
        // However, Java provides no way of doing so explicitly.  It will be done as part of the parsing of --release,
        // which is later than we want.
        this.release = release;
        this.sourceVersion = sourceVersion;
    }

    public int releaseInt() { return release; }
    /** Returns {@code SourceVersion.isName(name, this.sourceVersion)}. */
    public boolean isName(String name) { return SourceVersion.isName(name, sourceVersion); }

    /** Whether the release matches the version of the JRE. */
    public boolean matchesJreVersion() {
        return sourceVersion == SourceVersion.latest();
    }

    @Override
    public String toString() {
        return "Release{" +
                "release=" + release +
                ", sourceVersion=" + sourceVersion +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Release release1 = (Release) o;
        return release == release1.release && sourceVersion == release1.sourceVersion;
    }

    @Override
    public int hashCode() {
        // Seems like sourceVersion.hashCode() has some deterministic randomness to it.  Run clean, modco, modco,
        // the first modco always had one sourceVersion.hashCode() while the second had another.  They both had the same
        // value RELEASE_17.
        return Objects.hash(release, sourceVersion.name());
    }
}
