#!/bin/bash

set -e

declare DRY_RUN=false

function Fail {
    printf "$*"
    echo
    exit 1
}

function Run {
    local command="$1"
    shift

    if $DRY_RUN
    then
        printf "%q" "$command"
        printf " %q" "$@"
        printf "\\n"
    else
        "$command" "$@"
    fi
}

function Help {
    cat <<EOF
Usage: modulec.sh [OPTION...] SRC [-- JAVACARG...]
Create a modular JAR file from module source in SRC.

Options:
  [-C RSRC]...            Include each resource directory RSRC.
  -e,--main-class CLASS   Specify the qualified main class.  If CLASS starts
                          with '.' the main class will be MODULE.CLASS.
  -f,--file JARPATH       Write JAR file to JARPATH instead of the default
                          TARGET/MODULE[-VERSION].jar.
  -m,--manifest MANIFEST  Include the manifest information from MANIFEST file.
  -n,--dry-run            Print javac and jar equivalents without execution.
  -N,--name MODULE        Provide the module name.
  -o,--output OUTDIR      Output directory for generated files like class files
                          and the JAR file, by default target.
  -p,--module-path MPATH  The colon-separated module path used for compilation.
  -v,--version VERSION    The module version.
EOF

    exit 1
}

function Main {
    local -a resources=()
    local -a main_class=()
    local -a jarpath=()
    local -a manifest=()
    local module=
    local outdir=target
    local -a module_path=()
    local version_suffix=""
    local -a module_version=()
    local src=""

    (( $# > 0 )) || Help

    while (( $# > 0 ))
    do
        case "$1" in
            -C)
                resources+=(-C "$2" .)
                shift 2
                break
                ;;
            -e|--main-class)
                main_class+=("$1" "$2")
                shift 2
                ;;
            -f|--file)
                jarpath+=("$1" "$2")
                shift 2
                ;;
            -h|--help)
                Help
                ;;
            -m|--manifest)
                manifest+=("$1" "$2")
                shift 2
                ;;
            -n|--dry-run)
                DRY_RUN=true
                shift
                ;;
            -N|--name)
                module="$2"
                shift 2
                ;;
            -o|--output)
                outdir="$2"
                shift 2
                ;;
            -p|--module-path)
                module_path=("$1" "$2")
                shift 2
                ;;
            -v|--version)
                module_version+=(--module-version "$2")
                shift 2
                ;;
            -*)
                version_suffix="-$2"
                module_version=(--module-version "$2")
                shift 2
                ;;
            -*)
                Fail "Unknown option '$1'"
                ;;
            *)
                src="$1"
                shift
                break
                ;;
        esac
    done

    test "$src" != "" || Fail "error: No source directory"
    test -d "$src" || Fail "error: source directory does not exist"
    test -r "$src"/module-info.java || \
        Fail "error: no module-info.java in source directory"

    local newline=$'\n'
    if test "$module" == ""
    then
        # Heuristic getting the module name.
        [[ $(< "$src"/module-info.java) =~ (^|$newline)' '*(open *)?module' '+([a-zA-Z0-9._]+) ]] || \
            Fail "No module name found in $src/module-info.java, please" \
                 "provide one with --name"
        name="${BASH_REMATCH[3]}"
    fi

    if (( $# > 0 ))
    then
        test "$1" == -- || Fail "error: extraneous argument: $1"
        shift
    fi

    local -a javac_args=("$@")

    # Fall back to JAVA_HOME if not in PATH.
    if ! type javac &> /dev/null && \
                test "$JAVA_HOME" != "" && \
                test -x "$JAVA_HOME"/bin/javac
    then
        PATH="$JAVA_HOME"/bin:"$PATH"
    fi

    type java &> /dev/null || Fail "'java' is not installed"
    type javac &> /dev/null || Fail "'javac' is not installed"
    type jar &> /dev/null || Fail "'jar' is not installed"
    type realpath &> /dev/null || Fail "'realpath' is not installed"

    mkdir -p "$outdir"
    mkdir -p "$outdir"/javac-classes
    rm -f "$outdir"/javac-classes/"$name"
    ln -s ../classes "$outdir"/javac-classes/"$name"
    mkdir -p "$outdir"/javac-src
    rm -f "$outdir"/javac-src/"$name"
    ln -s "$(realpath -m "$src")" "$outdir"/javac-src/"$name"

    Run javac "${module_path[@]}" --module-source-path "$outdir"/javac-src \
        -m "$name" "${module_version[@]}" -d "$outdir"/javac-classes \
        "${javac_args[@]}"

    if (( ${#jarpath[@]} == 0 ))
    then
        jarpath=(-f "$outdir"/"$name$version_suffix".jar)
    fi

    # Resolve relative main class
    if (( ${#main_class[@]} > 0 )) && test "${main_class[1]:0:1}" == .
    then
        main_class[1]="$name${main_class[1]}"
    fi

    Run jar -c "${jarpath[@]}" "${manifest[@]}" "${main_class[@]}" \
        -C "$outdir"/classes . "${resources[@]}"
}

Main "$@"
