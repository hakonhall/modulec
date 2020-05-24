.PHONY: all install clean

all: bin/modulec-shebang target/mvn.ts install

# Used as a proxy for a mvn run
target/mvn.ts: Makefile \
               pom.xml \
               src/main/java/no/ion/modulec/ModuleCompiler.java \
               src/test/java/no/ion/modulec/ModuleCompilerTest.java \
               src/test/java/no/ion/modulec/BasicTest.java \
               src/test/java/no/ion/modulec/ModuleCompilerTest.java \
               src/test/java/no/ion/modulec/BasicTest.java \
               src/test/resources/help.txt \
               src/test/resources/module-info-valid.java \
               src/test/resources/basic/src/module-info.java \
               src/test/resources/basic/src/no/ion/tst1/Exported.java \
               src/test/resources/basic/manifest.mf \
               src/test/resources/basic/rsrc/README.txt \
               src/test/resources/module-info-invalid.java \
               src/main/java/module-info.java \
               src/main/java/no/ion/modulec/ModuleCompiler.java
	mvn -nsu clean install
	touch $@

bin/modulec-shebang: src/main/java/no/ion/modulec/ModuleCompiler.java
	printf "#!/home/hakon/share/jdk-11/bin/java --source 11\n\n" > $@
	cat $< >> $@
	chmod +x $@

install: ~/bin ~/bin/modulec

~/bin:
	@echo "Warning: Create ~/bin to install modulec there"

~/bin/modulec:
	ln -s $(PWD)/bin/modulec-wrapper.sh ~/bin/modulec

clean:
	rm -f ~/bin/modulec
	mvn -nsu clean
