import mill.*, mill.scalalib.*, mill.scalalib.scalafmt.*, mill.scalalib.publish.*

// Version management
object versions {
  val scala3 = "3.7.3"
  val cats = "2.13.0"
  val catsEffect = "3.6.3"
  val decline = "2.5.0"
  val pureconfig = "0.17.9"
  val http4s = "0.23.32"
  val fs2 = "3.12.2"
  val circe = "0.14.14"
  val log4cats = "2.7.1"
  val logback = "1.5.19"
  val rome = "2.1.0"
  val munit = "1.2.0"
  val munitCatsEffect = "2.1.0"
  val laika = "1.3.2"
  val graalvm = "java25"
}

// Note: In Mill, top-level modules are automatically aggregated
// No need for an explicit root module

// Main application module
object scalanews extends ScalaModule with PublishModule with ScalafmtModule {
  def scalaVersion = versions.scala3

  def mvnDeps = Seq(
    mvn"org.typelevel::cats-core:${versions.cats}",
    mvn"org.typelevel::cats-effect:${versions.catsEffect}",
    mvn"com.monovore::decline-effect:${versions.decline}",
    mvn"com.github.pureconfig::pureconfig-generic-scala3:${versions.pureconfig}",
    mvn"com.github.pureconfig::pureconfig-cats-effect:${versions.pureconfig}",
    mvn"com.github.pureconfig::pureconfig-http4s:${versions.pureconfig}",
    mvn"org.http4s::http4s-circe:${versions.http4s}",
    mvn"org.http4s::http4s-ember-client:${versions.http4s}",
    mvn"org.http4s::http4s-ember-server:${versions.http4s}",
    mvn"org.http4s::http4s-dsl:${versions.http4s}",
    mvn"co.fs2::fs2-core:${versions.fs2}",
    mvn"co.fs2::fs2-io:${versions.fs2}",
    mvn"io.circe::circe-core:${versions.circe}",
    mvn"io.circe::circe-generic:${versions.circe}",
    mvn"io.circe::circe-parser:${versions.circe}",
    mvn"org.typelevel::log4cats-slf4j:${versions.log4cats}",
    mvn"ch.qos.logback:logback-classic:${versions.logback}",
    mvn"com.rometools:rome:${versions.rome}"
  )

  def mainClass = Some("com.softinio.scalanews.Main")

  // Test trait for shared test configuration
  trait ScalaNewsTest extends ScalaTests with TestModule.Munit {
    def mvnDeps = Seq(
      mvn"org.scalameta::munit:${versions.munit}",
      mvn"org.typelevel::munit-cats-effect:${versions.munitCatsEffect}"
    )

    override def forkEnv = super.forkEnv() ++ Map(
      // Dynamically resolve project root (where build.sc is located)
      "SCALA_NEWS_CONFIG" -> s"${os.pwd.toString.replaceAll("/out/.*$", "")}/test-config.json"
    )

    def forkArgs = Seq("-Xmx2G")
  }

  // Main test module - runs all tests
  object tests extends ScalaNewsTest {
    def testFramework = "munit.Framework"
  }

  // Publishing configuration
  def publishVersion = "0.1.0"

  def pomSettings = PomSettings(
    description = "Scala News - A CLI tool for generating Scala newsletters",
    organization = "com.softinio",
    url = "https://github.com/softinio/scalanews",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("softinio", "scalanews"),
    developers = Seq(
      Developer("softinio", "Salar Rahmanian", "https://github.com/softinio")
    )
  )

  // Native image support
  def nativeImageOptions = Seq(
    "--no-fallback",
    "--enable-url-protocols=http",
    "--enable-url-protocols=https",
    "-H:+ReportExceptionStackTraces",
    "--initialize-at-build-time",
    "--no-server",
    "-H:-CheckToolchain"
  )

  def nativeImageName = "scalanews"

  def nativeImagePath = Task {
    os.pwd / "target" / nativeImageName
  }

  def nativeImage() = Task.Command {
    val assemblyPath = assembly().path
    val outputPath = nativeImagePath()
    val options = nativeImageOptions
    val mainClassValue = mainClass().getOrElse(
      throw new Exception("mainClass must be defined for native image")
    )

    // Ensure target directory exists
    os.makeDir.all(outputPath / os.up)

    // Find zlib in Nix store
    val zlibPath = os.proc("nix", "eval", "--raw", "nixpkgs#zlib.outPath")
      .call(cwd = os.pwd)
      .out
      .text()
      .trim

    // Build the native-image command
    val command = Seq("native-image") ++
      options ++
      Seq(
        s"-H:NativeLinkerOption=-L${zlibPath}/lib",
        "-H:DynamicProxyConfigurationResources=META-INF/native-image/com.softinio/scalanews/proxy-config.json",
        "-jar", assemblyPath.toString,
        "-H:Name=" + nativeImageName,
        "-H:Class=" + mainClassValue,
        outputPath.toString
      )

    // Execute native-image
    os.proc(command).call(cwd = os.pwd, stdout = os.Inherit, stderr = os.Inherit)

    println(s"Native image created at: $outputPath")

    // Make it executable
    os.perms.set(outputPath, "rwxr-xr-x")

    PathRef(outputPath)
  }
}

// Documentation module
object docs extends ScalaModule {
  def scalaVersion = versions.scala3

  // Build the documentation site
  def build() = Task.Command {
    val inputDir = "docs"
    val outputDir = "site/target/docs/site"

    println(s"Building documentation site...")
    println(s"  Input:  $inputDir")
    println(s"  Output: $outputDir")

    // Determine project root - Mill runs from the project root directory
    val projectRoot = os.pwd.toString match {
      case p if p.contains("/out/") =>
        // We're in Mill's output directory, need to find project root
        os.Path(p.split("/out/").head)
      case p => os.Path(p)
    }

    val scriptPath = projectRoot / "scripts" / "LaikaBuild.scala"

    // Run the Laika build script
    os.proc("scala-cli", "run", scriptPath.toString)
      .call(cwd = projectRoot, stdout = os.Inherit, stderr = os.Inherit)

    println(s"Site built at: $outputDir")
    PathRef(projectRoot / os.RelPath(outputDir))
  }

  // Preview the documentation site with auto-refresh
  def preview() = Task.Command {
    val inputDir = "docs"
    val port = 4242

    println(s"Starting preview server...")
    println(s"  Input: $inputDir")
    println(s"  URL:   http://localhost:$port")
    println(s"  Press Ctrl+C to stop")

    // Determine project root - Mill runs from the project root directory
    val projectRoot = os.pwd.toString match {
      case p if p.contains("/out/") =>
        // We're in Mill's output directory, need to find project root
        os.Path(p.split("/out/").head)
      case p => os.Path(p)
    }

    val scriptPath = projectRoot / "scripts" / "LaikaPreview.scala"

    // Run the Laika preview script
    os.proc("scala-cli", "run", scriptPath.toString)
      .call(cwd = projectRoot, stdout = os.Inherit, stderr = os.Inherit)
  }
}

// ============================================================================
// Mill Commands
// ============================================================================
//
// Basic commands:
//   mill scalanews.compile              - Compile the project
//   mill scalanews.tests.testCached     - Run all tests (cached)
//   mill scalanews.tests.testLocal      - Run tests without forking
//   mill scalanews.tests.testOnly       - Run specific test class
//   mill scalanews.run <args>           - Run the application
//   mill scalanews.reformat             - Format code
//   mill scalanews.checkFormat          - Check code formatting
//   mill scalanews.nativeImage          - Build GraalVM native image
//
// Documentation commands:
//   mill docs.build                     - Build documentation site
//   mill docs.preview                   - Preview documentation with auto-refresh
//
// Examples:
//   mill scalanews.run generate 2024-01-01 2024-01-07
//   mill scalanews.run create
//   mill scalanews.run publish 2024-01-07
//   mill scalanews.tests.testOnly "com.softinio.scalanews.BloggersSuite"
//   mill scalanews.nativeImage          - Creates ./target/scalanews executable
