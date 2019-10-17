# Makefile for those of us who can't be bothered to remember java build commands
#
# If I didn't suck at gradle I could probably make it manage the dependency chains here.
#
# Patches welcome.

# you can use system gradle, i won't judge
GRADLE=./gradlew
# GRADLE=gradle 

# change this to your favorite browser, or use open on a mac
OPEN=open

all:
	$(GRADLE) compileJava compileTestJava

build:
	$(GRADLE) compileJava

build-test:
	$(GRADLE) compileTestJava

run:
	$(GRADLE) run --args="Foo.cadenza"

test:
	$(GRADLE) test

clean:
	$(GRADLE) clean

jar:
	$(GRADLE) jar

component:
	$(GRADLE) component

install:
	$(GRADLE) installComponent

# build/graal/cadenza
native-image:
	$(GRADLE) nativeImage

docs:
	$(GRADLE) javadoc
	open build/docs/javadoc/index.html 

.PHONY: all build build-test clean docs jar component run test install
