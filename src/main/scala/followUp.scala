import java.nio.file.Paths
import java.io.{File, PrintWriter}
import scala.annotation.tailrec

import scala.util.Try
import com.github.nscala_time.time.Imports._
import mail._

object followUp {
  private val env: Map[String, Option[String]] = List("kroll_user", "kroll_pwd", "mailbot_user", "mailbot_pwd").
    map((x) => x -> sys.env.get(x)).
    toMap

  def check(args: Array[String]): Unit = {
    if (!env.values.forall(_.isDefined)) {
      throw new Exception("This user is missing the following environment variables: " +
        env.filter(_._2.isEmpty).keySet.mkString(", ")
      )
    }
    val check1 = Try(args(0).toInt).isSuccess
    val check2 = args(1).contains("@") && !args(1).startsWith("@") && !args(1).endsWith("@")
    val check3 = args(2).endsWith(".exe") & (Paths.get(args(2)).toFile() exists())
    val check4 = Paths.get(args(3)).toFile isDirectory()


    if (args.length == 4 && check1 && check2 && check3 && !check4) {
      println("All arguments are valid\n")
    } else {
        throw new Exception("Improper arguments. The first argument must be the number of days until call-back. " +
          "The second argument must be a proper email address to send the call-back alerts to. The third argument " +
          "must the Kroll Windows executable file location. The fourth argument must be an existing directory to " +
          "store temporary report files.")
    }
  }
  def runReport(exe: String, file: File, env: Map[String, Option[String]]): Unit = {
    import sys.process._

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

    val temp = new File("autoit_script.au3")
    temp.deleteOnExit()
    val p  = new PrintWriter(temp)
    p.write(autoit.mkString("\n"))
    p.close()

    s"AutoIt3.exe ${temp.getAbsolutePath()}" !
  }

  @tailrec
  def callers(file: File, callback_day: Int): Vector[Patient] = {
    if (file.exists()) {
      val regex = ",(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)"
      val raw = scala.io.Source.fromFile(file.getPath()).getLines().toVector
      val header = raw.take(1).flatMap(_.split(regex))
      val data: Vector[Patient] = raw.drop(1).map(a => Patient(a.split(regex).toVector, header))
      data.filter(_.shouldCall(callback_day))
    } else {
      // TODO: Add 1 minute time-out
      println("Waiting for report...")
      Thread.sleep(1000)
      callers(file, callback_day)
    }
  }
  def sendAlert(data: Vector[Patient], args: Array[String]): Unit = {
    val body = {
      if (data.length > 1) {
        "Follow-up with:\n" + data.map(_.prettyPrint()).mkString("\n\n")
      }
      else "No patients require a follow-up this week."
    }

    send a new Mail(
      from = (env("mailbot_user").get, "Scala Bot"),
      to = Seq(args(1)),
      subject = s"Patient (${args(0)}-day) follow-up alert",
      message = body,
      cred = env.filterKeys(List("mailbot_user", "mailbot_pwd").contains)
    )
  }

  def main(args: Array[String]): Unit = {
    args.foreach(println)
    check(args)
    val temp = new File(args(3), DateTimeFormat.forPattern("dd-MMMM-YY").print(DateTime.now()) + ".csv")
    temp.deleteOnExit()
    runReport(args(2), temp, env)
    val patients = callers(temp, args(0).toInt)
    sendAlert(patients, args)
  }
}