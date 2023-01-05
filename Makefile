DIRS = no.ion.modulec no.ion.modulec.junit

.PHONY: all install clean $(DIRS)


all: target = all
all: $(DIRS)


install: target = install
install: $(DIRS)


clean: target = clean
clean: $(DIRS)


$(DIRS):
	$(MAKE) -C $@ $(target)
