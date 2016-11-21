package pl.touk.esp.engine.marshall

import argonaut.PrettyParams
import cats.data.Validated.Valid
import org.scalatest.{FlatSpec, Inside, Matchers, OptionValues}
import pl.touk.esp.engine._
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.sink.SinkRef

import scala.concurrent.duration._

class ProcessMarshallerSpec extends FlatSpec with Matchers with OptionValues with Inside {

  import spel.Implicits._

  val ProcessMarshaller = new ProcessMarshaller
  it should "marshall and unmarshall to same process" in {

    def nestedGraph(id: String) =
      GraphBuilder
        .processor(id + "Processor", id + "Service")
        .sink(id + "End", "")

    val process =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("a", "")
        .filter("b", "alamakota == 'true'", nestedGraph("b"))
        .buildVariable("c", "fooVar", "f1" -> "expr1", "f2" -> "expr2")
        .enricher("d", "barVar", "dService", "p1" -> "expr3")
        .switch("f", "expr4", "eVar", nestedGraph("e"), Case("e1", GraphBuilder.sink("endE1", "")))

    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)
    println(marshalled)

    val unmarshalled = ProcessMarshaller.fromJson(marshalled).toOption
    val result = ProcessCanonizer.uncanonize(unmarshalled.value).toOption

    result should equal(Some(process))
  }

  it should "marshall and unmarshall to same process with ending processor" in {
    val process = EspProcessBuilder
            .id("process1")
            .exceptionHandler()
            .source("a", "")
            .processorEnd("d", "dService", "p1" -> "expr3")

    val result = marshallAndUnmarshall(process)

    result should equal(Some(process))
  }

  it should "omit additional fields" in {
    val withAdditionalFields =
      """
        |{
        |    "metaData" : { "id": "custom", "parallelism" : 2, "additionalFields": { "description": "process description"} },
        |    "exceptionHandlerRef" : { "parameters" : [ { "name": "errorsTopic", "value": "error.topic"}]},
        |    "nodes" : [
        |        {
        |            "type" : "Source",
        |            "id" : "start",
        |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "value": "in.topic"}]},
        |            "additionalFields": { "description": "single node description"}
        |        }
        |    ]
        |}
      """.stripMargin

    val withoutAdditionalFields =
      """
        |{
        |    "metaData" : { "id": "custom", "parallelism" : 2},
        |    "exceptionHandlerRef" : { "parameters" : [ { "name": "errorsTopic", "value": "error.topic"}]},
        |    "nodes" : [
        |        {
        |            "type" : "Source",
        |            "id" : "start",
        |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "value": "in.topic"}]}
        |        }
        |    ]
        |}
      """.stripMargin

    inside(ProcessMarshaller.fromJson(withAdditionalFields)) { case Valid(process) =>
      process.metaData.id shouldBe "custom"
      process.metaData.additionalFields shouldBe None
      process.nodes.head.data.additionalFields shouldBe None
    }

    inside(ProcessMarshaller.fromJson(withoutAdditionalFields)) { case Valid(process) =>
      process.metaData.id shouldBe "custom"
      process.metaData.additionalFields shouldBe None
      process.nodes.head.data.additionalFields shouldBe None
    }

  }


  def marshallAndUnmarshall(process: EspProcess): Option[EspProcess] = {
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)
    val unmarshalled = ProcessMarshaller.fromJson(marshalled).toOption
    ProcessCanonizer.uncanonize(unmarshalled.value).toOption
  }
}
