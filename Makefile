.PHONY: default mvn junit install clean

default: junit

mvn: bin/modulec-shebang target/mvn.ts install

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
# 	mvn -nsu clean
	rm -rf target


TEST_DEPS := target/no.ion.modulec-1.0.0.jar:lib/junit-platform-console-standalone-1.6.2.jar:lib/jimfs-1.1.jar:lib/guava-18.0.jar

TEST_FILES := src/test/java/no/ion/modulec/ModuleCompilerTest.java \
              src/test/java/no/ion/modulec/BasicTest.java

junit: bin/modulec-shebang target/junit.ts install

target/junit.ts: target/no.ion.modulec-1.0.0.jar \
                 lib/junit-platform-console-standalone-1.6.2.jar \
		 lib/jimfs-1.1.jar \
		 lib/guava-18.0.jar
	javac -d target/test-classes -cp $(TEST_DEPS) $(TEST_FILES)
	java -jar lib/junit-platform-console-standalone-1.6.2.jar --disable-banner -E junit-vintage --fail-if-no-tests --config junit.jupiter.execution.parallel.enabled=true -cp target/no.ion.modulec-1.0.0.jar -cp target/test-classes -cp lib/jimfs-1.1.jar -cp lib/guava-18.0.jar --scan-class-path target/test-classes
	touch $@

target/no.ion.modulec-1.0.0.jar: Makefile \
                 pom.xml \
                 src/main/java/module-info.java \
                 src/main/java/no/ion/modulec/ModuleCompiler.java
	bin/modulec.sh -v 1.0.0 src/main/java
