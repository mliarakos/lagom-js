package java.util.function

import scala.scalajs.js.annotation.JavaDefaultMethod

@FunctionalInterface
trait Consumer[T] { self =>
  def accept(t: T): Unit

  @JavaDefaultMethod
  def andThen(after: Consumer[_ >: T]): Consumer[T] = {
    new Consumer[T] {
      def accept(t: T): Unit = {
        self.accept(t)
        after.accept(t)
      }
    }
  }
}
