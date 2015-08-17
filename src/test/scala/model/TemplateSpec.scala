package model

import com.opentok.raven.model.Template
import org.scalatest.{Matchers, WordSpec}

class TemplateSpec extends WordSpec with Matchers {

  "Load and render a twirl template" in {
    Template("test.scala.html").toHtml.get should be("<h1>hello</h1><h1>world</h1>")
  }

  "Load a static template" in {
    Template("test.html").toHtml.get should be("<h1>TEST</h1>")
  }
}
