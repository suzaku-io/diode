import sbt.{CrossVersion, Def}
import sbt.Keys.scalaVersion

object Util {
  def scalaVerDependent[A](pf: PartialFunction[(Long, Long), A]) = Def.setting {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some(version) => pf.lift.apply(version)
      case None          => None
    }
  }

  def scalaVerDependentSeq[A](pf: PartialFunction[(Long, Long), Seq[A]]) = Def.setting {
    scalaVerDependent(pf).value.getOrElse(Seq.empty)
  }

}
