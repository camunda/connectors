/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/**
 * Generates @NullMarked package-info.java files into target/generated-sources/ for every
 * package that contains source files but does not already have a hand-written package-info.java.
 *
 * This gives IntelliJ the per-package @NullMarked signal it needs for null analysis (JSpecify does
 * not cascade from a parent package), while NullAway continues to use AnnotatedPackages for
 * build-time enforcement. Generated files are never committed — they live only in target/.
 */
def srcRoot = project.basedir.toPath().resolve('src/main/java').toFile()
def outRoot = new File(project.build.directory, 'generated-sources/nullaway-package-info')

srcRoot.eachDirRecurse { dir ->
  // Only generate for packages that actually contain source files
  def sourceFiles = dir.listFiles(
      { f -> !f.isDirectory() && f.name.endsWith('.java') && f.name != 'package-info.java' } as FileFilter
  )
  if (!sourceFiles?.length) return

  // Honour any hand-written package-info.java in the source tree
  if (new File(dir, 'package-info.java').exists()) return

  def relPath = srcRoot.toPath().relativize(dir.toPath())
  def pkg = relPath.toString().replace(File.separator, '.')
  def outDir = new File(outRoot, relPath.toString())
  outDir.mkdirs()
  new File(outDir, 'package-info.java').text =
      "@NullMarked\npackage ${pkg};\n\nimport org.jspecify.annotations.NullMarked;\n"
}

project.addCompileSourceRoot(outRoot.absolutePath)
