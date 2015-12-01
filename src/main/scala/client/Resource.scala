package client

import java.io.{File, InputStream}

import spray.http.DateTime

import scala.concurrent.Future
import scala.xml._


trait Resource {
  def property(prop:Node): Option[Node] = prop match {
    case t @ <resourcetype/> => Some(t)
    case _ => None
  }

  def url: String
  def children: Seq[Resource]
  def length: Int
  def date: DateTime
  def stream: Future[File]
}