package jgo.compiler
package parser.exprs

import scope._

import parser.types._
import parser.scoped._

import interm._
import interm.types._

trait PrimaryExprs extends Operands with TypeSyntax with Scoped with ExprUtils {
  self: Expressions =>
  
  lazy val primaryExpr: PP[Expr] =              "primary expression" $
    ( primaryExpr ~ selector                                                  //e.g. myStruct.field, or myPackage.value
    | primaryExpr ~ call                                                      //e.g. myFunc(param), int(5)
    | primaryExpr ~ index       ^^ mkIndex                                    //e.g. arr[0], myMap["hello"]
    | primaryExpr ~ slice                                                     //e.g. arr[2 : 5]
    | primaryExpr ~ typeAssert                                                //e.g. expr.(int)
//  | (goType <~ "(") ~ expression <~ ")"                     &@ "unambiguous type conversion"
//  | specialBuiltinTypeCall //not yet supported
    | operand
    )
  
  lazy val selector: P[String] =          "field or method selector" $
    "." ~> ident
  
  lazy val index: P[Expr] =                        "subscript/index" $ 
    "[" ~> expression <~ "]"
  
  lazy val slice: P[Option[Expr] ~ Option[Expr]] = "slice operation" $
    "[" ~> (expression.? <~ ":") ~ expression.? <~ "]"
  
  lazy val typeAssert: P[Type] =                    "type assertion" $
    "." ~> "(" ~> goType <~ ")"
  
  lazy val call: P[List[Expr]] =    "function call" $
    "(" ~> exprList <~ ")"
    //"(" ~> exprList ~ "...".? <~ ")" //again with the optional trailing comma!
  
  
  def mkCall(func: Expr, args: List[Expr]): Expr
  
  def mkIndex(arrBase: Expr, indx: Expr): Expr = {
    @inline
    def demandIntegral() {
      if (!indx.isOfType[IntegralType])
        recordErr("index type %s not an integral type", indx.t) //yes, integral is the correct type
    }
    
    @inline
    def demandKey(keyT: Type, mapT: Type) {
      if (!(keyT <<= indx.t))
        recordErr("index type %s not assignable to key type %s of map type %s", indx.t, keyT, mapT)
    }
    
    arrBase match {
      case HasType(ArrayType(_, t))    => demandIntegral(); ArrayIndexLval(arrBase, indx, t)
      case HasType(SliceType(t))       => demandIntegral(); SliceIndexLval(arrBase, indx, t)
      case HasType(mt @ MapType(k, v)) => demandKey(k, mt); MapIndexLval(arrBase, indx, v)
      case HasType(StringType)         => demandIntegral(); SimpleExpr(arrBase.eval |+| indx.eval |+| StrIndex, Uint8)
      case _ => badExpr("base type %s of index expression not array, slice, map, or string type", arrBase.t)
    }
  }
}