package com.opentok.raven.model

import scala.io.Source
import scala.util.Try

object Templates {

  class TemplateSourceError(reason: Exception) extends Exception(s"Could not source static template. Reason: $reason")

  sealed trait Template {
    val id: Long
    val name: String
  }

  abstract class TwirlTemplate(val id: Long, val name: String) extends Template {
    Try {
      Source.fromFile(classOf[Template].getClassLoader.getResource("templates/" + name + "scala.html").toURI)
    }.recover {
      case e: Exception ⇒ throw new TemplateSourceError(e)
    }.get
  }

  abstract class StaticTemplate(val id: Long, val name: String) extends Template {

    private def source(name: String): Try[String] = Try {
      Source.fromFile(classOf[Template].getClassLoader.getResource("templates/" + name + ".html").toURI).mkString
    }.recover {
      case e: Exception ⇒ throw new TemplateSourceError(e)
    }

    val body: String = source(name).get
    
  }

  case object test extends StaticTemplate(1000, "test")

  case object twirl_test extends TwirlTemplate(1001, "twirl_test") with ((String, Int) ⇒ String) {

    def apply(v1: String, v2: Int): String = html.twirl_test(v1, v2).body

    val body = (arg1: String, arg2: Int) ⇒ apply(arg1, arg2) 
  }

}
