import com.github.nscala_time.time.Imports._

case class Patient(rows: Vector[String], header: Vector[String]) {
  private lazy val name: String = rows(header.indexOf("Patient Name")).
    replaceAll("^\"|\"$", "").
    split(", ").
    reverse.
    mkString(" ")
  private lazy val number: String = rows( header.indexOf("Pat Phone #") ).replaceAll("\\^$", "")
  lazy val rx: Int = rows( header.indexOf("Rx") ).toInt
  private lazy val prescription: String = rows( header.indexOf("Drug Name") )
  val fillDate: DateTime = DateTimeFormat.forPattern("dd-MMMM-YY").parseDateTime( rows( header.indexOf("FillDate") ) )

  def shouldCall(callback: Int): Boolean = fillDate + callback.days < DateTime.now()
  def prettyPrint(verbose: Boolean = true): String = {
//    s"$name ($rx) is waiting for a callback at $number on their $prescription compound."
    if (verbose) s"\t$name ($rx) at $number for their $prescription compound."
    else s"\tPatient $rx"
  }
  override def toString() = s"$rx filled $fillDate"
}