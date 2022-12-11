package no.ion.modulec;

import no.ion.modulec.modco.Options;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class UsageException extends ModuleCompilerException {
    public static UsageException fromResource(String path) {
        InputStream inputStream = Options.class.getClassLoader().getResourceAsStream(path);
        Objects.requireNonNull(inputStream, "No such resource: " + path);
        byte[] bytes = uncheckIO(inputStream::readAllBytes);
        String help = new String(bytes, StandardCharsets.UTF_8);
        return new UsageException(help);
    }

    public UsageException(String usage) {
        super(usage);
    }
}
