import sbt.Keys._

lazy val commonSettings = Seq(
  organization  := "com.github.pheymann",
  version       := "0.1.0-RC1",
  scalaVersion  := "2.11.11",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "utf-8",
    "-explaintypes",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator",
    "-Xlint:option-implicit",
    "-Xlint:type-parameter-shadow",
    "-Xlint:unsound-match",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any"
  )
)

lazy val mavenSettings = Seq(
  sonatypeProfileName := "pheymann",
  publishMavenStyle   := true,
  pomExtra in Global  := {
    <url>https://github.com/pheymann/artie</url>
      <licenses>
        <license>
          <name>MIT</name>
          <url>https://github.com/pheymann/artie/blob/master/LICENSE</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:github.com/pheymann/artie</connection>
        <developerConnection>scm:git:git@github.com:pheymann/artie</developerConnection>
        <url>github.com/pheymann/artie</url>
      </scm>
      <developers>
        <developer>
          <id>pheymann</id>
          <name>Paul Heymann</name>
          <url>https://github.com/pheymann</url>
        </developer>
      </developers>
  }
)

lazy val artie = project
  .in(file("."))
  .settings(commonSettings: _*)
  .aggregate(core, framework, examples)

lazy val core = project
  .in(file("core"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    mavenSettings,
    Defaults.itSettings,
    name := "artie-core",
    libraryDependencies ++= Dependencies.core
  )

lazy val framework = project
  .in(file("framework"))
  .settings(
    commonSettings,
    mavenSettings,
    name := "artie"
  )
  .dependsOn(core)

lazy val examples = project
  .in(file("examples"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    libraryDependencies ++= Dependencies.examples
  )
  .dependsOn(framework)
