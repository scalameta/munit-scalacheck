package munit

class FrameworkSuite extends BaseFrameworkSuite {
  val tests: List[FrameworkTest] = List[FrameworkTest](
    ScalaCheckFrameworkSuite,
    ScalaCheckFrameworkShorthandSuite,
    ScalaCheckExceptionFrameworkSuite
  )
  tests.foreach { t => check(t) }
}
