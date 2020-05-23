.PHONY: all install clean

all: bin/modulec-shebang

install: ~/bin ~/bin/modulec

~/bin/modulec:
	ln -s $(PWD)/bin/modulec-wrapper.sh ~/bin/modulec

bin/modulec-shebang: target/classes/no/ion/modulec/ModuleCompiler.class
	printf "#!/home/hakon/share/jdk-11/bin/java --source 11\n\n" > $@
	cat $< >> $@
	chmod +x $@

target/classes/no/ion/modulec/ModuleCompiler.class: src/main/java/no/ion/modulec/ModuleCompiler.java src/test/java/no/ion/modulec/ModuleCompilerTest.java src/test/java/no/ion/modulec/BasicTest.java
	mvn -nsu clean install

clean:
	rm -f ~/bin/modulec
	rm -f bin/modulec-shebang
	mvn -nsu clean
