package java

/*
 * Use java.io.StringWriter as java.io.CharArrayWriter for JS compatibility.
 */
package object io {
  type CharArrayWriter = java.io.StringWriter
}
