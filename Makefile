.PHONY: all clean

all: clean
	mkdir bin
	javac -classpath src -d bin src/CacheSim.java

clean:
	rm -rf bin *.class
