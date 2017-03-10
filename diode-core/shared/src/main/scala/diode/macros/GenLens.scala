package diode.macros

import diode.ModelRW

import scala.reflect.macros.blackbox

object GenLens {
  def generate[M: c.WeakTypeTag, A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(field: c.Expr[A => B]): c.Expr[ModelRW[M, B]] = {
    def mkLens_impl[T](model: c.Expr[ModelRW[M, Any]], fieldName: c.Expr[String], modelType: c.WeakTypeTag[T]): c.Expr[ModelRW[M, Any]] = {
      import c.universe._

      val sTpe = modelType.tpe

      val strFieldName = c.eval(c.Expr[String](c.untypecheck(fieldName.tree.duplicate)))

      val fieldMethod = sTpe.decls
        .collectFirst {
          case m: MethodSymbol if m.isCaseAccessor && m.name.decodedName.toString == strFieldName => m
        }
        .getOrElse(c.abort(c.enclosingPosition, s"Cannot find method $strFieldName in $sTpe"))

      val constructor = sTpe.decls
        .collectFirst {
          case m: MethodSymbol if m.isPrimaryConstructor => m
        }
        .getOrElse(c.abort(c.enclosingPosition, s"Cannot find constructor in $sTpe"))

      val field = constructor.paramLists.head
        .find(_.name.decodedName.toString == strFieldName)
        .getOrElse(c.abort(c.enclosingPosition, s"Cannot find constructor field named $fieldName in $sTpe"))

      val res = c.Expr[ModelRW[M, Any]](q"""$model.zoomRW(_.$fieldMethod)((m, t) => m.copy($field = t))""")
      // println(showRaw(res))
      res
    }
    import c.universe._

    val model: c.Expr[ModelRW[M, Any]] = c.Expr[ModelRW[M, Any]](c.prefix.tree)

    /** Extractor for member select chains.
      *e.g.: SelectChain.unapply(a.b.c) == Some("a",Seq(a.type -> "b", a.b.type -> "c")) */
    object SelectChain {
      def unapply(tree: Tree): Option[(Name, Seq[(Type, TermName)])] = tree match {
        case Select(tail @ Ident(termUseName), field: TermName) =>
          Some((termUseName, Seq(tail.tpe.widen -> field)))
        case Select(tail, field: TermName) =>
          SelectChain
            .unapply(tail)
            .map(
              t => t.copy(_2 = t._2 :+ (tail.tpe.widen -> field))
            )
        case _ => None
      }
    }

    val res: c.Expr[ModelRW[M, B]] = field match {
      // _.field
      case Expr(
          Function(
            List(ValDef(_, termDefName, _, EmptyTree)),
            Select(Ident(termUseName), fieldNameName)
          )
          ) if termDefName.decodedName.toString == termUseName.decodedName.toString =>
        val fieldName = fieldNameName.decodedName.toString
        mkLens_impl(model, c.Expr[String](q"$fieldName"), implicitly[c.WeakTypeTag[A]]).asInstanceOf[c.Expr[ModelRW[M, B]]]

      // _.field1.field2...
      case Expr(
          Function(
            List(ValDef(_, termDefName, _, EmptyTree)),
            SelectChain(termUseName, typesFields)
          )
          ) if termDefName.decodedName.toString == termUseName.decodedName.toString =>
        // println(s"Fields: $typesFields")
        val finalModel = typesFields.foldLeft(model) {
          case (m, (t, f)) =>
            mkLens_impl(m, c.Expr[String](q"${f.decodedName.toString}"), c.WeakTypeTag(t))
        }
        finalModel.asInstanceOf[c.Expr[ModelRW[M, B]]]

      case _ => c.abort(c.enclosingPosition, s"Illegal field reference ${show(field.tree)}; please use _.field1.field2... instead")
    }
    // println(res)
    res
  }
}
