import Tests._

// This gives us a nicer handle to the root project instead of using the
// implicit one
lazy val consoleRoot = Project("consoleRoot", file("."))

// keep chisel/firrtl specific class files, rename other conflicts
val chiselFirrtlMergeStrategy = CustomMergeStrategy.rename { dep =>
  import sbtassembly.Assembly.{Project, Library}
  val nm = dep match {
    case p: Project => p.name
    case l: Library => l.moduleCoord.name
  }
  if (Seq("firrtl", "chisel3").contains(nm.split("_")(0))) { // split by _ to avoid checking on major/minor version
    dep.target
  } else {
    "renamed/" + dep.target
  }
}

lazy val commonSettings = Seq(
  organization := "vlsilab.ee.uec.ac",
  version := "0.4",
  scalaVersion := "2.13.10",
  assembly / test := {},
  assembly / assemblyMergeStrategy := {
    case PathList("chisel3", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    case PathList("firrtl", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    // should be safe in JDK11: https://stackoverflow.com/questions/54834125/sbt-assembly-deduplicate-module-info-class
    case x if x.endsWith("module-info.class") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Ymacro-annotations"), // fix hierarchy API
  unmanagedBase := (consoleRoot / unmanagedBase).value,
  allDependencies := {
    // drop specific maven dependencies in subprojects in favor of Chipyard's version
    val dropDeps = Seq(("edu.berkeley.cs", "rocketchip"))
    allDependencies.value.filterNot { dep =>
      dropDeps.contains((dep.organization, dep.name))
    }
  },
  exportJars := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal))

val rocketChipDir = file("hardware/chipyard/generators/rocket-chip")

lazy val firesimDir = file("hardware/chipyard/sims/firesim/sim/")

/**
  * It has been a struggle for us to override settings in subprojects.
  * An example would be adding a dependency to rocketchip on midas's targetutils library,
  * or replacing dsptools's maven dependency on chisel with the local chisel project.
  *
  * This function works around this by specifying the project's root at src/ and overriding
  * scalaSource and resourceDirectory.
  */
def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      Compile / scalaSource := baseDirectory.value / "main" / "scala",
      Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
    )
}

// Fork each scala test for now, to work around persistent mutable state
// in Rocket-Chip based generators
def isolateAllTests(tests: Seq[TestDefinition]) = tests map { test =>
  val options = ForkOptions()
  new Group(test.name, Seq(test), SubProcess(options))
} toSeq

val chiselVersion = "3.6.0"

lazy val chiselSettings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chiselVersion,
  "org.apache.commons" % "commons-lang3" % "3.12.0",
  "org.apache.commons" % "commons-text" % "1.9"),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full))


// Subproject definitions begin

// -- Rocket Chip --

lazy val hardfloat = freshProject("hardfloat", file("hardware/chipyard/generators/hardfloat/hardfloat"))
  .settings(chiselSettings)
  .dependsOn(midasTargetUtils)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    )
  )

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .dependsOn(hardfloat, rocketMacros, cde)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "mainargs" % "0.5.0",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "4.0.5",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test",
      "org.scala-graph" %% "graph-core" % "1.13.5"
    )
  )
  .settings( // Settings for scalafix
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused"
  )
lazy val rocketLibDeps = (rocketchip / Keys.libraryDependencies)


// -- Chipyard-managed External Projects --

// Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS
// TODO: Check
lazy val midasTargetUtils = (project in firesimDir / "midas" / "targetutils")
  .settings(chiselSettings)

lazy val testchipip = (project in file("hardware/chipyard/generators/testchipip"))
  .dependsOn(rocketchip, rocketchip_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val chipyard = (project in file("hardware/chipyard/generators/chipyard"))
  .dependsOn(testchipip, rocketchip, boom, hwacha, rocketchip_blocks, rocketchip_inclusive_cache, iocell,
    sha3, // On separate line to allow for cleaner tutorial-setup patches
    dsptools, rocket_dsp_utils,
    gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator,
    constellation, mempress, barf, shuttle, caliptra_aes)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(
    libraryDependencies ++= Seq(
      "org.reflections" % "reflections" % "0.10.2"
    )
  )
 .settings(commonSettings)

lazy val mempress = (project in file("hardware/chipyard/generators/mempress"))
  .dependsOn(rocketchip, midasTargetUtils)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val barf = (project in file("hardware/chipyard/generators/bar-fetchers"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val constellation = (project in file("hardware/chipyard/generators/constellation"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val fft_generator = (project in file("hardware/chipyard/generators/fft-generator"))
  .dependsOn(rocketchip, rocket_dsp_utils)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tracegen = (project in file("hardware/chipyard/generators/tracegen"))
  .dependsOn(testchipip, rocketchip, rocketchip_inclusive_cache, boom)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val icenet = (project in file("hardware/chipyard/generators/icenet"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val hwacha = (project in file("hardware/chipyard/generators/hwacha"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val boom = freshProject("boom", file("hardware/chipyard/generators/boom"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val shuttle = (project in file("hardware/chipyard/generators/shuttle"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val cva6 = (project in file("hardware/chipyard/generators/cva6"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val ibex = (project in file("hardware/chipyard/generators/ibex"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sodor = (project in file("hardware/chipyard/generators/riscv-sodor"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sha3 = (project in file("hardware/chipyard/generators/sha3"))
  .dependsOn(rocketchip, midasTargetUtils)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val gemmini = (project in file("hardware/chipyard/generators/gemmini"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val nvdla = (project in file("hardware/chipyard/generators/nvdla"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val caliptra_aes = (project in file("hardware/chipyard/generators/caliptra-aes-acc"))
  .dependsOn(rocketchip, rocc_acc_utils, testchipip, midasTargetUtils)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocc_acc_utils = (project in file("hardware/chipyard/generators/rocc-acc-utils"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val iocell = Project(id = "iocell", base = file("./hardware/chipyard/tools/barstools/") / "iocell")
  .settings(chiselSettings)
  .settings(commonSettings)

lazy val tapeout = (project in file("./hardware/chipyard/tools/barstools/"))
  .settings(chiselSettings)
  .settings(commonSettings)

lazy val fixedpoint = (project in file("./hardware/chipyard/tools/fixedpoint/"))
  .settings(chiselSettings)
  .settings(commonSettings)

lazy val dsptools = freshProject("dsptools", file("./hardware/chipyard/tools/dsptools"))
  .dependsOn(fixedpoint)
  .settings(
    chiselSettings,
    commonSettings,
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0",
      "org.scalatest" %% "scalatest" % "3.2.+" % "test",
      "org.typelevel" %% "spire" % "0.18.0",
      "org.scalanlp" %% "breeze" % "2.1.0",
      "junit" % "junit" % "4.13" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
  ))

lazy val cde = (project in file("hardware/chipyard/tools/cde"))
  .settings(commonSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "cde/src/chipsalliance/rocketchip")

lazy val rocket_dsp_utils = freshProject("rocket-dsp-utils", file("./hardware/chipyard/tools/rocket-dsp-utils"))
  .dependsOn(rocketchip, cde, dsptools)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocketchip_blocks = (project in file("hardware/chipyard/generators/rocket-chip-blocks"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocketchip_inclusive_cache = (project in file("hardware/chipyard/generators/rocket-chip-inclusive-cache"))
  .settings(
    commonSettings,
    Compile / scalaSource := baseDirectory.value / "design/craft")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)

// Library components of FireSim
/*lazy val midas      = ProjectRef(firesimDir, "midas")
lazy val firesimLib = ProjectRef(firesimDir, "firesimLib")

lazy val firechip = (project in file("generators/firechip"))
  .dependsOn(chipyard, midasTargetUtils, midas, firesimLib % "test->test;compile->compile")
  .settings(
    chiselSettings,
    commonSettings,
    Test / testGrouping := isolateAllTests( (Test / definedTests).value ),
    Test / testOptions += Tests.Argument("-oF")
  )*/
lazy val fpga_shells = (project in file("./hardware/chipyard/fpga/fpga-shells"))
  .dependsOn(rocketchip, rocketchip_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val fpga_platforms = (project in file("./hardware/chipyard/fpga"))
  .dependsOn(chipyard, fpga_shells)
  .settings(commonSettings)

lazy val riscvconsole = (project in file("hardware/riscvconsole"))
  .dependsOn(tapeout, chipyard, fpga_shells).
  settings(commonSettings)

