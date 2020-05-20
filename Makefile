.PHONY: all install

all:
	cd mod; mvn -nsu clean install

install: bin/modulec

bin/modulec: src/main/java/modulec.java bin
	printf "#!/home/hakon/share/jdk-11/bin/java --source 11\n\n" > $@
	cat $< >> $@
	chmod +x $@

bin:
	mkdir bin


clean:
	rm -rf bin
