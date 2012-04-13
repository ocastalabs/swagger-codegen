package com.wordnik.swagger.codegen.util

import com.wordnik.swagger.core._
import com.wordnik.swagger.core.util.JsonUtil

import scala.collection.mutable.{ HashSet, ListBuffer, HashMap }
import scala.collection.JavaConversions._

import scala.io.Source

object CoreUtils {
  val primitives = List("ok", "String", "Int")
  val containers = List("List", "Map", "Set", "Array")

  def m = JsonUtil.getJsonMapper

  def extractAllModels(baseDoc: Documentation): Map[String, DocumentationSchema] = {
    val modelObjects = new HashMap[String, DocumentationSchema]
    baseDoc.getApis.foreach(api => {
      val apiPath = (baseDoc.basePath + api.path).replaceAll(".\\{format\\}", ".json")
      val sd = m.readValue(Source.fromURL(apiPath).mkString, classOf[Documentation])
      for ((nm, model) <- extractModels(sd)) modelObjects += nm -> model
    })
    modelObjects.toMap
  }

  def extractModelNames(modelObjects: Map[String, DocumentationSchema], ep: DocumentationOperation): Set[String] = {
    val modelNames = new HashSet[String]

    modelNames += ep.responseClass
    // POST, PUT, DELETE body
    if (ep.getParameters != null)
      ep.getParameters.filter(p => p.paramType == "body")
        .foreach(p => modelNames += p.dataType)
        
    // extract all base model names, strip away Containers like List[] and primitives
    val ComplexTypeMatcher = ".*\\[(.*)\\].*".r
    val baseNames = (for (modelName <- (modelNames.toList))
      yield (modelName match {
      case ComplexTypeMatcher(basePart) => basePart
      case _ => modelName
    })).toSet
    
    // get complex models from base
    val requiredModels = modelObjects.filter(obj => baseNames.contains(obj._1))

    val subNames = new HashSet[String]
    // look inside top-level models
    requiredModels.map(model => {
      // add top level model
      subNames += model._1
      model._2.properties.foreach(prop => {
        val subObject = prop._2
        if (containers.contains(subObject.getType)) {
          if (subObject.items.ref != null)
            subNames += subObject.items.ref
          else
            subNames += subObject.items.getType
        } else subNames += subObject.getType
      })
    })
    subNames.toSet
  }

  def extractModels(sd: Documentation): Map[String, DocumentationSchema] = {
    val modelNames = new HashSet[String]
    val modelObjects = new HashMap[String, DocumentationSchema]
    // return types
    if (sd.getApis != null) {
      sd.getApis.foreach(api => {
        if (api.getOperations != null)
          api.getOperations.foreach(op => {
            modelNames += op.responseClass
            // POST, PUT, DELETE body
            if (op.getParameters != null)
              op.getParameters.filter(p => p.paramType == "body")
                .foreach(p => modelNames += p.dataType)
          })
      })
    }
    if (sd.getModels != null)
      for ((name, m) <- sd.getModels) modelObjects += name -> m

    // extract all base model names, strip away Containers like List[] and primitives
    val ComplexTypeMatcher = ".*\\[(.*)\\].*".r
    
    val baseNames = (for (modelName <- (modelNames.toList -- primitives))
      yield (modelName match {
      case ComplexTypeMatcher(basePart) => basePart
      case _ => modelName
    })).toSet

    // get complex models from base
    val requiredModels = modelObjects.filter(obj => baseNames.contains(obj._1))

    val subNames = new HashSet[String]
    // look inside top-level models
    requiredModels.map(model => {
      model._2.properties.foreach(prop => {
        val subObject = prop._2
        if (containers.contains(subObject.getType)) {
          if (subObject.items.ref != null)
            subNames += subObject.items.ref
          else
            subNames += subObject.items.getType
        } else subNames += subObject.getType
      })
    })

    val subModels = modelObjects.filter(obj => subNames.contains(obj._1))

    val allModels = requiredModels ++ subModels

    // TODO: put these in a base object
    val primitiveTypes = Set("Int", "String", "Long", "Double", "Float", "Boolean")
    allModels.filter(m => primitiveTypes.contains(m._1) == false).toMap
  }
}