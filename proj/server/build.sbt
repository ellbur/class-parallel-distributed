
name := "misty"

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

resolvers ++= Seq(
    // For Scalaz
    "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/",
    // For Akka
    "Akka repo"  at "http://akka.io/repository/",
    "Multiverse" at "http://multiverse.googlecode.com/svn/maven-repository/releases/",
    "GuicyFruit" at "http://guiceyfruit.googlecode.com/svn/repo/releases/",
    "JBoss"      at "https://repository.jboss.org/nexus/content/groups/public/"
)

libraryDependencies ++= Seq(
    "joda-time"        %  "joda-time"           % "2.0",
    "org.joda"         %  "joda-convert"        % "1.0",
    "org.slf4j"        % "slf4j-log4j12"         % "1.6.1",
    "org.scalaz"       % "scalaz-core_2.9.1"         % "6.0.3",
    "se.scalablesolutions.akka" % "akka" % "1.2",
    "org.apache.commons" % "commons-math" % "2.2",
    "net.liftweb"      % "lift-json-ext_2.9.1"       % "2.4-M4"
)

// vim:syn=scala

