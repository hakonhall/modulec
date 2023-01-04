package no.ion.modulec.modco;

import no.ion.modulec.Context;
import no.ion.modulec.MessageSink;
import no.ion.modulec.file.Pathname;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

public class ProgramContext implements Context {
    private final FileSystem fileSystem;
    private final MessageSink standardOut;

    private volatile boolean showCommands = true;
    private volatile boolean showDebug = true;
    private volatile boolean showMilestones = true;

    public ProgramContext() {
        this.fileSystem = FileSystems.getDefault();
        this.standardOut = new StandardOut(() -> true, () -> showDebug, () -> showMilestones, () -> showCommands);
    }

    @Override public FileSystem fileSystem() { return fileSystem; }
    public Pathname pathname(String pathname) { return Pathname.of(fileSystem, pathname); }
    public Pathname pathOf(String pathname) { return Pathname.of(fileSystem, pathname); }

    @Override public MessageSink log() { return standardOut; }

    public boolean showCommands() { return showCommands; }
    public boolean showDebug() { return showDebug; }
    public boolean showMilestones() { return showMilestones; }

    public void showCommands(boolean show) { this.showCommands = show; }
    public void showDebug(boolean show) { this.showDebug = show; }
    public void showMilestones(boolean show) { this.showMilestones = show; }
}
