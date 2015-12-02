package client

import spray.http.DateTime
import spray.json._

object FileTreeJson extends DefaultJsonProtocol {

  implicit object FileNodeJsonFormat extends RootJsonFormat[FileNode] {
    override def read(json: JsValue): FileNode = {
      json.asJsObject.getFields("id", "name", "size", "modifiedDate") match {
        case Seq(JsString(id), JsString(name), JsNumber(size), JsNumber(jsonModifiedDate)) =>
          FileNode(id, name, size.toInt, DateTime(jsonModifiedDate.toLong))
        case _ =>
          throw new DeserializationException("FileNode expected")
      }
    }

    override def write(obj: FileNode): JsValue = JsObject(
        "id" -> JsString(obj.id),
        "nodeType" -> JsString("file"),
      "name" -> JsString(obj.name),
      "size" ->  JsNumber(obj.size),
      "modifiedDate" -> JsNumber(obj.modifiedDate.clicks)
    )
  }

  implicit object FolderNodeJsonFormat extends RootJsonFormat[FolderNode] {

    import FileSystemNodeFormat._

    override def read(json: JsValue): FolderNode = {
      json.asJsObject.getFields("id", "name", "childs", "modifiedDate") match {
        case Seq(JsString(id), JsString(name), JsArray(childs), JsNumber(jsonModifiedDate)) =>
          if (childs.isEmpty) {
            LazyFolderNode(id, name, DateTime(jsonModifiedDate.toLong))
          } else {
            StaticFolderNode(id, name, childs.map(_.convertTo[FileSystemNode]), DateTime(jsonModifiedDate.toLong))
          }

        case _ =>
          throw new DeserializationException("FolderNode expected")
      }
    }

    override def write(obj: FolderNode): JsValue = {
      val childs = JsArray(obj.childs.map(_.toJson).toVector)

      JsObject(
        "nodeType" -> JsString("folder"),
        "id" -> JsString(obj.id),
        "name" -> JsString(obj.name),
        "childs" -> childs,
        "modifiedDate" -> JsNumber(obj.modifiedDate.clicks)
      )
    }
  }

  implicit object FileSystemNodeFormat extends RootJsonFormat[FileSystemNode] {
    override def read(json: JsValue): FileSystemNode = {
      val jsonObject = json.asJsObject

      jsonObject.getFields("nodeType") match {
        case Seq(JsString(nodeType)) =>
          nodeType match {
            case "folder" =>
              jsonObject.convertTo[FolderNode]
            case "file" =>
              jsonObject.convertTo[FileNode]
            case _ =>
              throw new DeserializationException("Type expected")
          }
      }
    }

    override def write(obj: FileSystemNode): JsValue = obj match {
      case f: FolderNode =>
        f.toJson
      case f: FileNode =>
        f.toJson
      case _ =>
        throw new DeserializationException("Unknown type")
    }
  }
}