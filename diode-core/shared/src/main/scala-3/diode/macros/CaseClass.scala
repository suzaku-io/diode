package diode.macros

import scala.quoted.*

private[macros] object CaseClass {

  def apply[Q <: Quotes](using q: Q)(obj: q.reflect.Term): Either[String, CaseClass[q.type]] = {
    if (obj.tpe.typeSymbol.caseFields.isEmpty) {
      Left(s"Type ${obj.tpe.typeSymbol.fullName} of ${obj.tpe.show} field must be a case class.")
    } else {
      Right(new CaseClass[q.type]()(obj))
    }
  }

}

private[macros] class CaseClass[Q <: Quotes] private (using val q: Q)(obj: q.reflect.Term) {

  import q.reflect.*

  private val typeSymbol = obj.tpe.typeSymbol
  private val caseFields = typeSymbol.caseFields

  private def memberMethod(name: String): Symbol = {
    typeSymbol
      .memberMethod(name)
      .headOption
      .getOrElse(
        report.throwError(s"Expected a case class ${typeSymbol.fullName} to have '${name}' member.")
      )
  }

  def applyCopy(fieldName: String, fieldValue: Term): Apply = {
    val args = caseFields.zipWithIndex.map { (field, idx) =>
      if (field.name == fieldName) {
        NamedArg(fieldName, fieldValue)
      } else {
        Select(obj, memberMethod("copy$default$" + (idx + 1)))
      }
    }
    val copy = memberMethod("copy")
    Apply(Select(obj, copy), args)
  }

  def selectField(fieldName: String): Select = {
    Select(obj, caseFields.find(_.name == fieldName).get)
  }
}
