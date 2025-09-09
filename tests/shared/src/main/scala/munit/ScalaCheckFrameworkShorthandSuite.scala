package munit

import org.scalacheck.Prop.forAll

class ScalaCheckFrameworkShorthandSuite extends ScalaCheckSuite {

  // NOTE(gabro): this is needed for making the test output stable for the failed test below.
  // It also serves as a test for overriding this parameter
  override val scalaCheckInitialSeed =
    "CTH6hXj8ViScMmsO78-k4_RytXHPK_wSJYNH2h4dCpB="

  property("boolean check (true)") = forAll { (l1: List[Int], l2: List[Int]) =>
    l1.size + l2.size == (l1 ::: l2).size
  }

  property("boolean check (false)") = forAll { (n: Int) =>
    scala.math.sqrt(n * n) == n
  }

  property("tagged".tag(new Tag("a"))) = forAll { (n: Int) => n + 0 == n }

  property("assertions (true)") = forAll { (n: Int) =>
    assertEquals(n * 2, n + n)
    assertEquals(n * 0, 0)
  }

  property("assertions (false)") = forAll { (n: Int) =>
    assertEquals(n * 1, n)
    assertEquals(n * n, n)
    assertEquals(n + 0, n)
  }
}

object ScalaCheckFrameworkShorthandSuite
    extends FrameworkTest(
      classOf[ScalaCheckFrameworkShorthandSuite],
      """|==> success munit.ScalaCheckFrameworkShorthandSuite.boolean check (true)
         |==> failure munit.ScalaCheckFrameworkShorthandSuite.boolean check (false) - tests/shared/src/main/scala/munit/ScalaCheckFrameworkShorthandSuite.scala:16
         |15:
         |16:  property("boolean check (false)") = forAll { (n: Int) =>
         |17:    scala.math.sqrt(n * n) == n
         |
         |Failing seed: CTH6hXj8ViScMmsO78-k4_RytXHPK_wSJYNH2h4dCpB=
         |You can reproduce this failure by adding the following override to your suite:
         |
         |  override def scalaCheckInitialSeed = "CTH6hXj8ViScMmsO78-k4_RytXHPK_wSJYNH2h4dCpB="
         |
         |Falsified after 0 passed tests.
         |> ARG_0: -1
         |> ARG_0_ORIGINAL: 2147483647
         |==> success munit.ScalaCheckFrameworkShorthandSuite.tagged
         |==> success munit.ScalaCheckFrameworkShorthandSuite.assertions (true)
         |==> failure munit.ScalaCheckFrameworkShorthandSuite.assertions (false) - tests/shared/src/main/scala/munit/ScalaCheckFrameworkShorthandSuite.scala:29
         |28:    assertEquals(n * 1, n)
         |29:    assertEquals(n * n, n)
         |30:    assertEquals(n + 0, n)
         |values are not the same
         |=> Obtained
         |1
         |=> Diff (- expected, + obtained)
         |--1
         |+1
         |
         |Failing seed: CTH6hXj8ViScMmsO78-k4_RytXHPK_wSJYNH2h4dCpB=
         |You can reproduce this failure by adding the following override to your suite:
         |
         |  override def scalaCheckInitialSeed = "CTH6hXj8ViScMmsO78-k4_RytXHPK_wSJYNH2h4dCpB="
         |
         |Falsified after 0 passed tests.
         |> ARG_0: -1
         |> ARG_0_ORIGINAL: 2147483647
         |""".stripMargin
    )
