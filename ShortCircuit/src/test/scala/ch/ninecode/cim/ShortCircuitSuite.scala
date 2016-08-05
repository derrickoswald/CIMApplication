package ch.ninecode

import scala.xml._

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

class ShortCircuitSuite extends FunSuite
{
  /**
   * Link to the scaladoc - very clear and detailed tutorial of FunSuite
   *
   * http://doc.scalatest.org/2.2.4/index.html#org.scalatest.FunSuite
   *
   * Operators
   *  - test
   *  - ignore
   *  - pending
   */
    test ("Basic")
    {
        assert (1 === 1)
    }

}
