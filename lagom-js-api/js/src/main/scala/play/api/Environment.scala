package play.api

import java.io.File

/*
 * Skeleton of [[play.api.Environment]] removing all functionality for JS compatibility.
 */
case class Environment(rootPath: File, classLoader: ClassLoader, mode: Mode)

object Environment {
  def simple(path: File = new File("."), mode: Mode = Mode.Test) =
    Environment(path, new ClassLoader() {}, mode)
}
