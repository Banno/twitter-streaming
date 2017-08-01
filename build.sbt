name := "twitter"

scalaVersion := "2.11.11"

val http4sVersion = "0.15.16a"

(fork in Test) := true

(javaOptions in Test) ++= Seq("-Xmx4g", "-Xss1G")

libraryDependencies ++= Seq(
  "org.http4s"            %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"            %% "http4s-argonaut"     % http4sVersion,
  "org.spire-math"        %% "jawn-argonaut"       % "0.11.0",
  "com.twitter"           %% "algebird-core"       % "0.12.3",
  "ch.qos.logback"         % "logback-classic"     % "1.1.8"        % "runtime",
  "org.scalatest"         %% "scalatest"           % "3.0.1"        % "test",
  "org.scalacheck"        %% "scalacheck"          % "1.13.4"       % "test"
)
