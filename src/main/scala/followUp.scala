import java.io.File
import scala.annotation.tailrec

import scala.util.Try
import com.github.nscala_time.time.Imports._
import mail._

object followUp {
  private val env: Map[String, Option[String]] = List("kroll_user", "kroll_pwd", "mailbot_user", "mailbot_pwd").
    map((x) => x -> sys.env.get(x)).
    toMap
  val formatter = DateTimeFormat.forPattern("dd-MMMM-YY")
  val file = {
    val dir = new File(System.getProperty("user.home"), "Documents")
    new File(dir.getPath(), formatter.print(DateTime.now()) + ".csv")
  }
  file.deleteOnExit()

  def check(args: Array[String]): Unit = {
    if (!env.values.forall(_.isDefined)) {
      throw new Exception("This user is missing the following environment variables: " +
        env.filter(_._2.isEmpty).keySet.mkString(", ")
      )
    }
    if (args.length != 2 || Try(args(0).toInt).isFailure || !args(1).contains("@") || args(1).startsWith("@") ||
      args(1).endsWith("@")) {
        throw new Exception("Improper arguments. The first argument must be the number of days until call-back. " +
          "The second argument must be a proper email address to send the call-back alerts to.")
    }
  }
  def runReport(file: File, env: Map[String, Option[String]]): Unit = {
//    TODO Write AutoIt script using the 3 variables given
    val p = new java.io.PrintWriter(file)
    val v = scala.io.Source.fromFile("/home/chris/Desktop/test.csv").getLines().toVector.mkString("\n")
    p.write(v)
    p.close
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
    check(args)
    runReport(file, env)
    val patients = callers(file, args(0).toInt)
    sendAlert(patients, args)
  }
}