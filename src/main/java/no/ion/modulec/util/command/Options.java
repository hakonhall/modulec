package no.ion.modulec.util.command;

import java.util.ArrayList;
import java.util.List;

public class Options {
    private final TopLevelOptions topLevelOptions = new TopLevelOptions();
    private final List<ModuleOptions> moduleOptions = new ArrayList<>();

    public Options() {}

    public List<ModuleOptions> moduleOptions() { return List.copyOf(moduleOptions); }

    public TopLevelOptions topLevelOptions() { return topLevelOptions; }

    public ModuleOptions addModuleOptions() {
        var moduleOptions = new ModuleOptions(topLevelOptions);
        this.moduleOptions.add(moduleOptions);
        return moduleOptions;
    }
}
