.PHONY: all clean

all: clean
	mkdir build
	javac -classpath src -d build src/CacheSim.java

clean:
	rm -rf build
