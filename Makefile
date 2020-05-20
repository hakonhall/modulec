.PHONY: mvn-compile install

all: mvn-compile install

mvn-compile:
	mvn -nsu clean install

install: bin/modulec-shebang

bin/modulec-shebang: src/main/java/modulec.java bin
	printf "#!/home/hakon/share/jdk-11/bin/java --source 11\n\n" > $@
	cat $< >> $@
	chmod +x $@
