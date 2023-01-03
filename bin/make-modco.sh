#!/bin/bash

cat <<EOF > "$1"
#!/bin/bash
exec java --class-path "\$0":$PWD/lib/no.ion.jhms-3.0.0.jar no.ion.modulec.ModuleCompiler3 "\$@"
EOF
