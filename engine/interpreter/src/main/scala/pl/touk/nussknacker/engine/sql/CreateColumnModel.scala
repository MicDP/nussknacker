package pl.touk.nussknacker.engine.sql

import java.util.Date

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult, TypingResult, Unknown}
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

object CreateColumnModel {

  def apply(typingResult: TypingResult): Validated[InvalidateMessage, ColumnModel] = {
    getListInnerType(typingResult).andThen {
      case typed: Typed =>
        TypedClassColumnModel.create(typed).valid
      case typedMap: TypedMapTypingResult =>
        TypedMapColumnModel.create(typedMap).valid
      case Unknown =>
        UnknownInner.invalid
    }
  }

  private[sql] val getListInnerType: TypingResult => Validated[InvalidateMessage, TypingResult] = {
    case t@Typed(klasses) if klasses.size == 1 =>
      val headClass = klasses.head
      val isCollection =
        classOf[Traversable[_]].isAssignableFrom(headClass.klass) ||
        classOf[java.util.Collection[_]].isAssignableFrom(headClass.klass)
      if (isCollection)
        headClass.params.headOption match {
          case Some(typ) => typ.valid
          case None => UnknownInner.invalid
        }
      else {
        notAList(t)
      }
    case t => notAList(t)
  }

  sealed trait InvalidateMessage

  case class NotAListMessage(typingResult: TypingResult) extends InvalidateMessage

  def notAList(typingResult: TypingResult): Validated[NotAListMessage, TypingResult] = NotAListMessage(typingResult).invalid

  object UnknownInner extends InvalidateMessage

  object ClazzToSqlType extends LazyLogging {
    val STRING: ClazzRef = ClazzRef[String]
    val INTEGER: ClazzRef = ClazzRef[Int]
    val LONG: ClazzRef = ClazzRef[Long]
    val DOUBLE: ClazzRef = ClazzRef[Double]
    val BIG_DECIMAL: ClazzRef = ClazzRef[BigDecimal]
    val J_BIG_DECIMAL: ClazzRef = ClazzRef[java.math.BigDecimal]
    val J_LONG: ClazzRef = ClazzRef[java.lang.Long]
    val J_INTEGER: ClazzRef = ClazzRef[java.lang.Integer]
    val J_DOUBLE: ClazzRef = ClazzRef[java.lang.Double]
    val J_BOOLEAN: ClazzRef = ClazzRef[java.lang.Boolean]
    val BOOLEAN: ClazzRef = ClazzRef[Boolean]
    val NUMBER: ClazzRef = ClazzRef[Number]
    val DATE: ClazzRef = ClazzRef[Date]

    def convert(name: String, arg: ClazzRef): Option[SqlType] = {
      import SqlType._
      arg match {
        case STRING =>
          Some(Varchar)
        case NUMBER =>
          Some(Decimal)
        case DOUBLE | BIG_DECIMAL |
           J_DOUBLE | J_BIG_DECIMAL =>
          Some(Decimal)
        case INTEGER | LONG |
           J_INTEGER | J_LONG  =>
          Some(Numeric)
        case BOOLEAN |
             J_BOOLEAN =>
          Some(Bool)
        case DATE =>
          Some(SqlType.Date)
        case a =>
          logger.warn(s"no mapping for name: $name and type $a")
          None
      }
    }
  }

}
