package no.ion.modulec.java;

import javax.lang.model.SourceVersion;

public class JavaLang {
    public static boolean isIdentifier(String string) {
        if (string.isEmpty()) {
            return false;
        }

        int[] codePoints = string.codePoints().toArray();
        if (!Character.isJavaIdentifierStart(codePoints[0])) {
            return false;
        }

        for (int i = 1; i < codePoints.length; ++i) {
            if (!Character.isJavaIdentifierPart(i)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidClassName(String className) {
        String[] identifiers = className.split("\\.", -1);
        for (var identifier : identifiers) {
            // TODO: This ought to match the release/source/target of the compilation
            if (!SourceVersion.isName(identifier, SourceVersion.RELEASE_11)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidPackageName(String packageName) {
        return isValidClassName(packageName);
    }
}
