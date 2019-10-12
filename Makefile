# Makefile for those of us who can't be bothered to remember java build commands
#
# you can use system gradle, i won't judge
GRADLE=./gradlew
# GRADLE=gradle 

# change this to your favorite browser, or open on a mac
OPEN=open

all:
	$(GRADLE) compileJava compileTestJava

build:
	$(GRADLE) compileJava

build-test:
	$(GRADLE) compileTestJava

run:
	$(GRADLE) run

test:
	$(GRADLE) test

clean:
	$(GRADLE) clean

jar:
	$(GRADLE) jar

component: jar
	$(JAVA_HOME)/bin/gu -L install build/libs/core.jar 

docs:
	$(GRADLE) javadoc
	open build/docs/javadoc/index.html 

.PHONY: all build build-test clean docs jar component run test

