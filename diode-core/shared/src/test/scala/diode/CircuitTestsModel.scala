package diode

object CircuitTestsModel {

  case class Model(s: String, data: Data)

  case class Data(i: Int, b: Boolean)
}
