#!/bin/bash
#exec java --class-path "$0" no.ion.modulec.ModuleCompiler3 "$@"
exec java --class-path "$0":$HOME/local/github/hakonhall/modulec/lib/no.ion.jhms-3.0.0.jar no.ion.modulec.ModuleCompiler3 "$@"
