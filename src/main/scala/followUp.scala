import java.nio.file.Paths
import java.io.{File, PrintWriter}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.Try
import com.github.nscala_time.time.Imports._
import mail._

object followUp {
  private val env: Map[String, Option[String]] = List("kroll_user", "kroll_pwd", "mailbot_user", "mailbot_pwd").
    map((x) => x -> sys.env.get(x)).toMap

  def check(args: Array[String]): Unit = {
    assert(!env.values.forall(_.isDefined),
      "This user is missing the following environment variables:" + env.filter(_._2.isEmpty).keySet.mkString(", "))
    assert(args.length == 4, "4 arguments are required")
    assert(Try(args(0).toInt).isSuccess, "First argument must be the number of days until call-back")
    assert(args(1).contains("@") && !args(1).startsWith("@") && !args(1).endsWith("@"),
      "Second argument must be a valid email address to send follow-up alerts to")
    assert(args(2).endsWith(".exe") & Paths.get(args(2)).toFile().canExecute(),
      "Third argument must be the path to the Kroll Windows executable file")
    assert(Paths.get(args(3)).toFile.isDirectory(),
      "Fourth argument must be an existing directory to store temporary report files")
  }
  def runReport(exe: String, path: String, env: Map[String, Option[String]]): File = {
    import sys.process._
    import scala.concurrent.ExecutionContext.Implicits.global

    val file = new File(path, DateTimeFormat.forPattern("dd-MMMM-YY").print(DateTime.now()) + ".csv")
    file.deleteOnExit()
    if (file.exists()) {
      file.delete()
    }

    val autoit = List(
      raw"""Run("$exe")""",
      """WinWaitActive("[TITLE:Login; CLASS:TLoginForm]")""",
      raw"""Send("${env("kroll_user").get}{TAB}")""",
      raw"""Send("${env("kroll_pwd").get}{ENTER}")""",
      """local $update = WinWaitActive("[TITLE:Program Update Pending; CLASS:TButtonForm]", "", 5)""",
      "If $update <> 0 Then",
      """ControlClick($update, "", "[CLASS:TBitBtn; INSTANCE:1]")""",
      "EndIf",
      """Send("!r")""",
      """Send("rd")""",
      """Send("^r")""",
      """Send("y")""",
      """Send("^a")""",
      """Send("last week")""",
      """Send("+{TAB}")""",
      """Send("^u")""",
      """Send("{F2}")""",
      """Send("^d")""",
      """Send("compounds{ENTER 2}")""",
      """Send("^o")""",
      """Send("{TAB 5}{SPACE}")""",
      """Send("{TAB 3}{DOWN 2}")""",
      """Send("^c")""",
      """WinWaitActive("Save CSV File")""",
      raw"""Send("${file.getAbsolutePath()}{ENTER}")""",
      "Sleep(500)",
      """Send("{ESC}")""",
      "Sleep(500)",
      """Send("!{F4}")""",
      "Sleep(500)",
      """Send("y")""",
      """Send("n")"""
    )

    val script = new File("autoit_script.au3")
    script.deleteOnExit()
    val p  = new PrintWriter(script)
    p.write(autoit.mkString("\n"))
    p.close()

    Await.result(Future(s"AutoIt3.exe ${script.getAbsolutePath()}" !), Duration(120, SECONDS))

    if (!file.exists()) {
      throw new Exception("AutoIt3 script has failed. Has Kroll been updated?")
    }
    file
  }

  def callers(file: File, callback_day: Int): Vector[Patient] = {
    val regex = ",(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)"
    val raw = scala.io.Source.fromFile(file.getPath()).getLines().toVector
    val header = raw.take(1).flatMap(_.split(regex))
    val data: Vector[Patient] = raw.drop(1).map(a => Patient(a.split(regex).toVector, header))
    data.filter(_.shouldCall(callback_day))
  }

  def sendAlert(data: Vector[Patient], callback_day: Int, addr: String): Unit = {
    val body = {
      if (data.length > 1) {
        "Follow-up with:\n" + data.map(_.prettyPrint()).mkString("\n\n")
      }
      else "No patients require a follow-up this week."
    }

    send a new Mail(
      from = (env("mailbot_user").get, "Scala Bot"),
      to = addr,
      subject = s"Patient ($callback_day-day) follow-up alert",
      message = body,
      cred = env.filterKeys(List("mailbot_user", "mailbot_pwd").contains)
    )
  }

  def main(args: Array[String]): Unit = {
    check(args)
    val report = runReport(args(2), args(3), env)
    val patients = callers(report, args(0).toInt)
    sendAlert(patients, args(0).toInt, args(1))
  }
}