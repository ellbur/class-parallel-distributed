
import sbt._

object myapp extends Build {
    lazy val root = Project("", file(".")) dependsOn interface
    lazy val interface = file("../interface")
}

