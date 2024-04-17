package munit

class FrameworkSuite extends BaseFrameworkSuite {
  val tests: List[FrameworkTest] = List[FrameworkTest](
    ScalaCheckFrameworkSuite,
    ScalaCheckExceptionFrameworkSuite
  )
  tests.foreach { t => check(t) }
}
