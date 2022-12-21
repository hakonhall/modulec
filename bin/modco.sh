#!/bin/bash
exec java --class-path "$0":$HOME/local/github/hakonhall/hybridmodules/no.ion.jhms/target/no.ion.jhms-3.0.0.jar no.ion.modulec.ModuleCompiler3 "$@"
