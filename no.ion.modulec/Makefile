JAVA_SOURCES = $(shell find src/main/java -name '*.java')
TEST_SOURCES = $(shell find src/test/java -name '*.java')
BASIC_SOURCES = src/test/resources/basic/manifest.mf \
                $(shell find src/test/resources/basic/src -type f) \
                $(shell find src/test/resources/basic/rsrc -type f)
BAD_MODULE_INFO = $(shell find src/test/resources/bad-module-info)
TEST_FILES := $(TEST_SOURCES) $(BASIC_SOURCES) $(BAD_MODULE_INFO)

TEST_DEPS := target/no.ion.modulec-1.0.0.jar:lib/junit-platform-console-standalone-1.6.2.jar:lib/jimfs-1.1.jar:lib/guava-18.0.jar

.PHONY: default mvn junit clean install uninstall target/no.ion.modulec-1.0.0.jar

default: junit

junit: ~/bin target/modc target/junit.ts target/modco

target/modc: target/no.ion.modulec-1.0.0.jar
	cat $^ > $@
	chmod +x $@

target/no.ion.modulec-1.0.0.jar:
	mvn install

#target/no.ion.modulec-1.0.0.jar: Makefile pom.xml $(JAVA_SOURCES)
#	bin/modulec.sh -v 1.0.0 -e no.ion.modulec.ModuleCompiler2 src/main/java

bin/modulec-shebang: src
	printf "#!/home/hakon/share/jdk-11/bin/java --source 11\n\n" > $@
	cat $< >> $@
	chmod +x $@

target/junit.ts: target/no.ion.modulec-1.0.0.jar $(TEST_FILES)
	javac -d target/test-classes -cp $(TEST_DEPS) $(TEST_SOURCES)
	java -jar lib/junit-platform-console-standalone-1.6.2.jar --disable-banner -E junit-vintage --fail-if-no-tests --config junit.jupiter.execution.parallel.enabled=true -cp target/no.ion.modulec-1.0.0.jar -cp target/test-classes -cp lib/jimfs-1.1.jar -cp lib/guava-18.0.jar --scan-class-path target/test-classes
	touch $@

target/modco: target/no.ion.modulec-1.0.0.jar
	bin/make-modco.sh $@
	cat $< >> $@
	chmod +x $@

~/bin:
	mkdir $@

~/bin/modulec:
	rm -f ~/bin/modulec
	ln -s $(PWD)/bin/modulec-wrapper.sh ~/bin/modulec

~/bin/modc: target/modc ~/bin
	rm -f $@
	cp $< $@

~/bin/modco: target/modco ~/bin
	rm -f $@
	cp $< $@

clean:
	rm -rf target

install: ~/bin ~/bin/modulec ~/bin/modc ~/bin/modco

uninstall:
	rm -f ~/bin/modulec
