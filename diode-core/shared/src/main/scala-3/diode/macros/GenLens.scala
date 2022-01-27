package diode.macros

import diode.{FastEq, ModelRW, ZoomTo}

import scala.quoted.*

private[diode] object GenLens {

  def generate[RootModel: Type, Model: Type, Field: Type](
      field: Expr[Model => Field],
      zoomer: Expr[ZoomTo[RootModel, Model]],
      feq: Expr[FastEq[? >: Field]]
  )(using quotes: Quotes): Expr[ModelRW[RootModel, Field]] = {

    import quotes.reflect.*

    def selectChain(term: Term): Option[Vector[String]] = {
      term match {
        case Select(term, name) => selectChain(term).map(_ :+ name)
        case Ident(_)           => Some(Vector.empty[String])
        case _                  => None
      }
    }

    def copyLoop(obj: Term, fieldName: String, tail: List[String], value: Term): Term = {
      val caseClass = CaseClass(obj).fold(msg => report.errorAndAbort(msg, field), identity)

      val fieldValue = {
        if (tail.isEmpty) value
        else copyLoop(caseClass.selectField(fieldName), tail.head, tail.tail, value)
      }

      caseClass.applyCopy(fieldName, fieldValue)
    }

    def reportIllegalFieldReference(): Nothing = {
      report.errorAndAbort("Illegal field reference, please use _.field1.field2... instead.", field)
    }

    def generateSetExpression(fieldChain: Term): Expr[(Model, Field) => Model] = {
      val fields = selectChain(fieldChain).getOrElse(reportIllegalFieldReference()).toList

      if (fields.isEmpty) {
        reportIllegalFieldReference()
      } else {
        '{ (model: Model, newValue: Field) =>
          ${
            copyLoop(
              obj = '{ model }.asTerm,
              fieldName = fields.head,
              tail = fields.tail.toList,
              value = '{ newValue }.asTerm
            ).asExpr.asInstanceOf[Expr[Model]]
          }
        }
      }
    }

    field.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(fieldChain))), _)) =>
        val setExpr = generateSetExpression(fieldChain)
        '{ $zoomer.zoomRW($field)($setExpr)($feq) }

      case _ => reportIllegalFieldReference()
    }

  }

}
