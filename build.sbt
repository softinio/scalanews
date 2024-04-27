import laika.ast._
import laika.ast.Path._
import laika.ast.InternalTarget
import laika.helium.Helium
import laika.helium.config.Favicon
import laika.helium.config.HeliumIcon
import laika.helium.config.IconLink

// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.1" // your current series x.y

ThisBuild / organization := "com.softinio"
ThisBuild / organizationName := "Salar Rahmanian"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("softinio", "Salar Rahmanian")
)

ThisBuild / githubWorkflowPublishTargetBranches := Seq()

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.corretto("21"))

val Scala3 = "3.3.3"
ThisBuild / crossScalaVersions := Seq(Scala3)
ThisBuild / scalaVersion := Scala3 // the default Scala

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "scalanews",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "com.monovore" %% "decline-effect" % "2.4.1",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.6",
      "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.6",
      "org.http4s" %% "http4s-ember-client" % "0.23.26",
      "org.http4s" %% "http4s-dsl" % "0.23.26",
      "co.fs2" %% "fs2-core" % "3.10.2",
      "co.fs2" %% "fs2-io" % "3.10.2",
      "com.rometools" % "rome" % "2.1.0",
      "org.scalameta" %% "munit" % "1.0.0-RC1" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.0.0-M5" % Test
    ),
    Compile / mainClass := Some("com.softinio.scalanews.Main"),
    nativeImageVersion := "21.0.2",
    nativeImageJvm := "graalvm-java21",
    nativeImageOptions += "--no-fallback",
    nativeImageOptions += "--enable-url-protocols=http",
    nativeImageOptions += "--enable-url-protocols=https",
    nativeImageOutput := file(".") / "scalanews",
    nativeImageReady := { () => println("SBT Finished creating image.") }
    // resolvers +=
    // "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )
  .enablePlugins(NativeImagePlugin)

lazy val docs = project
  .in(file("site"))
  .settings(
    tlSiteRelatedProjects := Seq(
      TypelevelProject.CatsEffect,
      "sbt-typelevel" -> url("https://github.com/typelevel/sbt-typelevel"),
      "decline" -> url("https://ben.kirw.in/decline/"),
      "Laika" -> url("https://planet42.github.io/Laika/")
    ),
    tlSiteHeliumConfig := {
      tlSiteHeliumConfig.value.all
        .metadata(
          title = Some("Scala News"),
          language = Some("en")
        )
        .site
        .topNavigationBar(
          homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home),
          navLinks = Seq(
            IconLink.external(
              "https://github.com/softinio/scalanews",
              HeliumIcon.github
            )
          )
        )
        .site
        .favIcons(
          Favicon.internal(Root / "img/favicon-32x32.png", sizes = "32x32")
        )
    }
  )
  .enablePlugins(TypelevelSitePlugin)
