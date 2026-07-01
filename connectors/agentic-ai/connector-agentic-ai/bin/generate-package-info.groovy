/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/**
 * Generates @NullMarked package-info.java into src/main/java for every package that contains
 * source files but does not already have one. Idempotent; commit the generated files.
 * Run after adding a new package to keep coverage complete.
 */
def srcRoot = project.basedir.toPath().resolve('src/main/java').toFile()

srcRoot.eachDirRecurse { dir ->
  def sourceFiles = dir.listFiles(
      { f -> !f.isDirectory() && f.name.endsWith('.java') && f.name != 'package-info.java' } as FileFilter
  )
  if (!sourceFiles?.length) return
  if (new File(dir, 'package-info.java').exists()) return
  def relPath = srcRoot.toPath().relativize(dir.toPath())
  def pkg = relPath.toString().replace(File.separator, '.')
  new File(dir, 'package-info.java').text =
      "@NullMarked\npackage ${pkg};\n\nimport org.jspecify.annotations.NullMarked;\n"
}
