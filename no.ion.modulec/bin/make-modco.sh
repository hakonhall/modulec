#!/bin/bash

cat <<EOF > "$1"
#!/bin/bash
exec java --class-path "\$0":$PWD/lib/no.ion.jhms-3.1.jar no.ion.modulec.ModuleCompiler3 "\$@"
EOF
