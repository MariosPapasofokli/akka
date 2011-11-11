/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.util

import java.io.{ PrintWriter, StringWriter }
import java.util.Comparator

/**
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
object Helpers {

  implicit def null2Option[T](t: T): Option[T] = Option(t)

  def compareIdentityHash(a: AnyRef, b: AnyRef): Int = {
    /*
     * make sure that there is no overflow or underflow in comparisons, so 
     * that the ordering is actually consistent and you cannot have a 
     * sequence which cyclically is monotone without end.
     */
    val diff = ((System.identityHashCode(a) & 0xffffffffL) - (System.identityHashCode(b) & 0xffffffffL))
    if (diff > 0) 1 else if (diff < 0) -1 else 0
  }

  val IdentityHashComparator = new Comparator[AnyRef] {
    def compare(a: AnyRef, b: AnyRef): Int = compareIdentityHash(a, b)
  }

  def intToBytes(value: Int): Array[Byte] = {
    val bytes = new Array[Byte](4)
    bytes(0) = (value >>> 24).asInstanceOf[Byte]
    bytes(1) = (value >>> 16).asInstanceOf[Byte]
    bytes(2) = (value >>> 8).asInstanceOf[Byte]
    bytes(3) = value.asInstanceOf[Byte]
    bytes
  }

  def bytesToInt(bytes: Array[Byte], offset: Int): Int = {
    (0 until 4).foldLeft(0)((value, index) ⇒ value + ((bytes(index + offset) & 0x000000FF) << ((4 - 1 - index) * 8)))
  }

  def ignore[E: Manifest](body: ⇒ Unit) {
    try {
      body
    } catch {
      case e if manifest[E].erasure.isAssignableFrom(e.getClass) ⇒ ()
    }
  }

  def withPrintStackTraceOnError(body: ⇒ Unit) {
    try {
      body
    } catch {
      case e: Throwable ⇒
        val sw = new java.io.StringWriter()
        var root = e
        while (root.getCause ne null) root = e.getCause
        root.printStackTrace(new java.io.PrintWriter(sw))
        System.err.println(sw.toString)
        throw e
    }
  }

  /**
   * Convenience helper to cast the given Option of Any to an Option of the given type. Will throw a ClassCastException
   * if the actual type is not assignable from the given one.
   */
  def narrow[T](o: Option[Any]): Option[T] = {
    require((o ne null), "Option to be narrowed must not be null!")
    o.asInstanceOf[Option[T]]
  }

  /**
   * Convenience helper to cast the given Option of Any to an Option of the given type. Will swallow a possible
   * ClassCastException and return None in that case.
   */
  def narrowSilently[T: Manifest](o: Option[Any]): Option[T] =
    try {
      narrow(o)
    } catch {
      case e: ClassCastException ⇒
        None
    }

  /**
   * Reference that can hold either a typed value or an exception.
   *
   * Usage:
   * <pre>
   * scala> ResultOrError(1)
   * res0: ResultOrError[Int] = ResultOrError@a96606
   *
   * scala> res0()
   * res1: Int = 1
   *
   * scala> res0() = 3
   *
   * scala> res0()
   * res3: Int = 3
   *
   * scala> res0() = { println("Hello world"); 3}
   * Hello world
   *
   * scala> res0()
   * res5: Int = 3
   *
   * scala> res0() = error("Lets see what happens here...")
   *
   * scala> res0()
   * java.lang.RuntimeException: Lets see what happens here...
   *    at ResultOrError.apply(Helper.scala:11)
   *    at .<init>(<console>:6)
   *    at .<clinit>(<console>)
   *    at Re...
   * </pre>
   */
  class ResultOrError[R](result: R) {
    private[this] var contents: Either[R, Throwable] = Left(result)

    def update(value: ⇒ R) {
      contents = try {
        Left(value)
      } catch {
        case (error: Throwable) ⇒ Right(error)
      }
    }

    def apply() = contents match {
      case Left(result) ⇒ result
      case Right(error) ⇒ throw error.fillInStackTrace
    }
  }
  object ResultOrError {
    def apply[R](result: R) = new ResultOrError(result)
  }
}
