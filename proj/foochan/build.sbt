
name := "foochan"

scalaVersion := "2.9.1"

target <<= baseDirectory(_ / ".target")

scalaSource in Compile <<= baseDirectory

includeFilter in Compile in unmanagedSources <<=
	(includeFilter in unmanagedSources, baseDirectory) { (ff, baseDir) =>
		ff && new SimpleFileFilter({ file =>
			val path = Path.relativizeFile(baseDir, file).get.getPath
			(
                   ! path.contains("test" + java.io.File.separator)
                && ! path.startsWith("project")
            )
		})
	}

scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Xexperimental"
)

// vim:syn=scala

