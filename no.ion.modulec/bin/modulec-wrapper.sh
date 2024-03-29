#!/bin/bash

echo "DEPRECATED: You should use modc instead." >&2

function Fail {
    echo "$*" >&2
    exit 1
}

type readlink &> /dev/null || Fail "Missing dependency: readlink"
type java &> /dev/null || Fail "Missing dependency: java"

# To launch the Java JVM with the modulec main class, we need to locate it.
# We'll assume modulec.class is in ../target/classes relative to the directory
# this file is in, so the task is reduced to finding what directory we're in.
#
# 'readlink -m "$0/.."' has been verified to work for these cases:
# 1. PWD is somewhere else, and a relative path is used to refer to this file.
# 2. PWD is somewhere else, and a symlink contains the relative path to this
# file.
# 3. PWD is somewhere else and PATH contains this directory.
#
# Therefore, the class path must be set to...
class_path=$(readlink -m "$0/.."/../target/classes)

if ! test -r "$class_path"/no/ion/modulec/ModuleCompiler.class
then
    Fail "error: modulec file does not exist: $class_path/no/ion/modulec/ModuleCompiler.class"
fi

exec java -cp "$class_path" no.ion.modulec.ModuleCompiler "$@"
