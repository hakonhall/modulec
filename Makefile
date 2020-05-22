.PHONY: all clean

all: bin/modulec-shebang

bin/modulec-shebang: target/classes/no/ion/modulec/ModuleCompiler.class
	printf "#!/home/hakon/share/jdk-11/bin/java --source 11\n\n" > $@
	cat $< >> $@
	chmod +x $@

target/classes/no/ion/modulec/ModuleCompiler.class: src/main/java/no/ion/modulec/ModuleCompiler.java
	mvn -nsu clean install

clean:
	rm -f bin/modulec-shebang
	mvn -nsu clean
