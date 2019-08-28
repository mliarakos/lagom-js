/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copy of [[play.api.mvc.Codec]] for JS compatibility.
 * https://github.com/playframework/playframework/blob/master/core/play/src/main/scala/play/api/mvc/Results.scala
 */

package play.api.mvc

import akka.util.ByteString

/**
 * A Codec handle the conversion of String to Byte arrays.
 *
 * @param charset The charset to be sent to the client.
 * @param encode The transformation function.
 */
case class Codec(charset: String)(val encode: String => ByteString, val decode: ByteString => String)

/**
 * Default Codec support.
 */
object Codec {

  /**
   * Create a Codec from an encoding already supported by the JVM.
   */
  def javaSupported(charset: String) =
    Codec(charset)(str => ByteString.apply(str, charset), bytes => bytes.decodeString(charset))

  /**
   * Codec for UTF-8
   */
  implicit val utf_8 = javaSupported("utf-8")

  /**
   * Codec for ISO-8859-1
   */
  val iso_8859_1 = javaSupported("iso-8859-1")

}
