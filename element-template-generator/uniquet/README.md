# Uniquet

`uniquet` goes through a github repository checking for all different versions of all elements-template and provides a single file containing references to all versions

## Installation

Currently, the only way to install `uniquet` is to build it from source.

### Local build

To build `uniquet` locally, check out the repository and build the project with Maven:

```shell
mvn install -pl element-template-generator/uniquet -am
```

This will build the `uniquet` module and all its dependencies.

Navigate to the `element-template-generator/uniquet/target/appassembler` directory.
The executable `uniquet` script is located in the `bin` directory. The compiled Java code required for
running `uniquet` is located in the `repo` directory. Make sure to copy both directories to your application installation directory.

Executables for Windows and Unix systems are provided (`.bat` and Shell scripts, respectively).

## Usage

`uniquet` has to be run at root of a git repository, It will crawl through every files inside `element-templates` directories for every commit and compile latest version of each of them in a single file

### Examples

`--branch` or `-b` is the branch one wants to start from. not required, default `main`
`--destination` or `-d` is the location where the file will be generated.
`--git-repository` or `-g` is the location of the git repository.

```shell
uniquet --destination ~/Desktop/singlefile.json --branch main
```
