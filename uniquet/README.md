# Uniquet

  **`uniquet`** is a utility that scans a GitHub repository to identify all versions of files located in `element-templates` directories.
It generates a single consolidated file that references all element-template's versions.

## Installation

At the moment, `uniquet` can only be installed by building it from source.

### Local Build

To build `uniquet` locally:

1. Clone the repository.
2. Use Maven to build the project:

```shell
mvn install -pl uniquet -am
```

This command will build the `uniquet` module along with all necessary dependencies.

After the build completes, navigate to:

```shell
element-template-generator/uniquet/target/appassembler
```

The `bin` directory contains the executable scripts for both Windows (`.bat`) and Unix-like systems (shell scripts).  
The `repo` directory contains the compiled Java code required to run `uniquet`.  
**Important:** Make sure to copy both the `bin` and `repo` directories to your application installation path.

## Usage

`uniquet` must be executed from the root of a Git repository.  
It recursively traverses the repository’s history, locating all `element-templates` directories in each commit.  
The tool then compiles the latest version of each template into a single output file.

### Command-Line Options

- `--directory` or `-d`:  
  Directory name to search for `element-templates`.  
  *(Optional; default is `connectors`.)*

- `--output-file` or `-o`:  
  Path where the consolidated output file will be generated.

- `--git-directory` or `-g`:  
  Git directory

### Example

```shell
./element-template-generator/uniquet/target/appassembler/bin/uniquet \
  --output-file ./connectors-file.json \
  --directory connectors \
  --git-directory ""
```
