package analysis

import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement
import org.eclipse.cdt.core.dom.ast.IASTDeclaration
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIncludeStatement
import org.eclipse.cdt.core.dom.ast.IASTProblem
import org.eclipse.cdt.core.dom.ast.IASTStatement
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage
import org.eclipse.cdt.core.dom.parser.c.ANSICParserExtensionConfiguration
import org.eclipse.cdt.core.dom.parser.c.ICParserExtensionConfiguration
import org.eclipse.cdt.core.index.IIndexFileLocation
import org.eclipse.cdt.core.model.ILanguage
import org.eclipse.cdt.core.parser.FileContent
import org.eclipse.cdt.core.parser.IParserLogService
import org.eclipse.cdt.core.parser.IScannerInfo
import org.eclipse.cdt.core.parser.ParserFactory
import org.eclipse.cdt.internal.core.parser.IMacroDictionary
import org.eclipse.cdt.internal.core.parser.InternalParserUtil
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContent
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContentProvider
import org.eclipse.core.runtime.CoreException

import org.eclipse.cdt.core.dom.ast._
import org.eclipse.cdt.internal.core.dom.parser.c._

import Util._

/*
  Parsing C seems to work. Now need to make sense of it.
  Structured form: eliminate gotos
      first compute cfg, then dominators, then reconstruct while/if
*/

object CFrontend {

  val language: ILanguage = GCCLanguage.getDefault() // GNUC

  val parserLog: IParserLogService = ParserFactory.createDefaultLogService();

  def wrapCode(pFileName: String, pCode: String): FileContent = {
     FileContent.create(pFileName, pCode.toCharArray());
  }

  def wrapFile(pFileName: String): FileContent = {
    wrapCode(pFileName, readFile(pFileName));
  }

  val PARSER_OPTIONS = (
              ILanguage.OPTION_IS_SOURCE_UNIT |     // our code files are always source files, not header files
              ILanguage.OPTION_NO_IMAGE_LOCATIONS) // we don't use IASTName#getImageLocation(), so the parse doesn't need to create them


  object StubScannerInfo extends IScannerInfo {
    def getDefinedSymbols(): java.util.Map[String,String] = {
      //val macrosBuilder = com.google.common.collect.ImmutableMap.builder[String,String]()
      //macrosBuilder.build()
      new java.util.HashMap
    }
    def getIncludePaths(): Array[String] = new Array(0)
  }

  object FileContentProvider extends InternalFileContentProvider {
    def getContentForInclusion(pFilePath: IIndexFileLocation,pAstPath: String): InternalFileContent = 
      InternalParserUtil.createFileContent(pFilePath)
    def getContentForInclusion(pFilePath: String,pMacroDictionary: IMacroDictionary): InternalFileContent = 
      InternalParserUtil.createExternalFileContent(pFilePath,
            InternalParserUtil.SYSTEM_DEFAULT_ENCODING);
  }

  def parseCFile(name: String) =
    language.getASTTranslationUnit(wrapFile(name), 
      StubScannerInfo, FileContentProvider, null, PARSER_OPTIONS, parserLog)


  def recurse(node: org.eclipse.cdt.core.dom.ast.IASTNode, indent: String = ""): Unit = {
      println(indent + node.getClass.getName.replaceAll("org.eclipse.cdt.internal.core.dom.parser.c.","") + ": " + node.getSyntax)
      for (y <- node.getChildren) recurse(y, indent + "  ")
  }


  def prettyPrintDefault(node: IASTNode): Unit = {
    //import org.eclipse.cdt.internal.core.dom.rewrite.astwriter._
    val w = new ASTWriterVisitor
    node.accept(w)
    val s = w.toString
    //val s = w.write(node)
    println(s)
  }






  val types = Array(
  "t_unspecified", // = 0;
  "t_void", // = 1;
  "t_char", // = 2;
  "t_int", // = 3;
  "t_float", // = 4;
  "t_double", // = 5;
  "t_bool", // = 6;
  "t_wchar_t", // = 7;
  "t_typeof", // = 8;
  "t_decltype", // = 9;
  "t_auto", // = 10;
  "t_char16_t", // = 11;
  "t_char32_t" // = 12;
  )
  assert(types.length == 13)

  /*
  // An integer literal e.g. 5
  public static final int lk_integer_constant = 0;
  // A floating point literal e.g. 6.0
  public static final int lk_float_constant = 1;
  // A char literal e.g. 'a'
  public static final int lk_char_constant = 2;
  // A string literal e.g. "a literal"
  public static final int lk_string_literal = 3;
  // <code>lk_this</code> represents the 'this' keyword for  c++ only.
  public static final int lk_this = 4;
  // <code>lk_true</code> represents the 'true' keyword.
  public static final int lk_true = 5;
  // <code>lk_false</code> represents the 'false' keyword.
  public static final int lk_false = 6;
  */



  val operators1 = Array(
  // Prefix increment.
  // <code>op_prefixIncr</code> ++exp
  "op_prefixIncr", // = 0;
  // Prefix decrement.
  // <code>op_prefixDecr</code> --exp
  "op_prefixDecr", // = 1;
  // Operator plus.
  // <code>op_plus</code> ==> + exp
  "op_plus", // = 2;
  // Operator minus.
  // <code>op_minux</code> ==> -exp
  "op_minus", // = 3;
  //  Operator star.
  //  <code>op_star</code> ==> *exp
  "op_star", // = 4;
  // Operator ampersand.
  // <code>op_amper</code> ==> &exp
  "op_amper", // = 5;
  // Operator tilde.
  // <code>op_tilde</code> ==> ~exp
  "op_tilde", // = 6;
  // not.
  // <code>op_not</code> ==> ! exp
  "op_not", // = 7;
  // sizeof.
  // <code>op_sizeof</code> ==> sizeof exp  
  "op_sizeof", // = 8;
  // Postfix increment.
  // <code>op_postFixIncr</code> ==> exp++
  "op_postFixIncr", // = 9;
  // Postfix decrement.
  // <code>op_bracketedPrimary</code> ==> exp--
  "op_postFixDecr", // = 10;
  // A bracketed expression.
  // <code>op_bracketedPrimary</code> ==> ( exp )
  "op_bracketedPrimary", // = 11;
  // for c++, only. <code>op_throw</code> throw exp
  "op_throw", // = 12;
  // for c++, only. <code>op_typeid</code> = typeid( exp )
  "op_typeid", // = 13;
  // @deprecated Shall not be used, 'typeof something' is not an expression, it's a declaration specifier.
  "op_typeof", // = 14;
  // for gnu parsers, only. <code>op_alignOf</code> is used for __alignOf( unaryExpression ) type
  // expressions.
  "op_alignOf", // = 15;
  // For c++, only: 'sizeof...(parameterPack)'
  // @since 5.2
  "op_sizeofParameterPack" // = 16;
  )
  assert(operators1.length == 17)




  val operators2 = Array("ERROR", // empty
  // multiply *
  "op_multiply", // = 1;
  // divide /
  "op_divide", // = 2;
  // modulo %
  "op_modulo", // = 3;
  // plus +
  "op_plus", // = 4;
  // minus -
  "op_minus", // = 5;
  // shift left <<
  "op_shiftLeft", // = 6;
  // shift right >>
  "op_shiftRight", // = 7;
  // less than <
  "op_lessThan", // = 8;
  // greater than >
  "op_greaterThan", // = 9;
  // less than or equals <=
  "op_lessEqual", // = 10;
  // greater than or equals >=
  "op_greaterEqual", // = 11;
  // binary and &rec
  "op_binaryAnd", // = 12;
  // binary Xor ^
  "op_binaryXor", // = 13;
  // binary Or |
  "op_binaryOr", // = 14;
  // logical and &&
  "op_logicalAnd", // = 15;
  // logical or ||
  "op_logicalOr", // = 16;
  // assignment =
  "op_assign", // = 17;
  // multiply assignment *=
  "op_multiplyAssign", // = 18;
  // divide assignemnt /=
  "op_divideAssign", // = 19;
  // modulo assignment %=
  "op_moduloAssign", // = 20;
  // plus assignment +=
  "op_plusAssign", // = 21;
  // minus assignment -=
  "op_minusAssign", // = 22;
  // shift left assignment <<=
  "op_shiftLeftAssign", // = 23;
  // shift right assign >>=
  "op_shiftRightAssign", // = 24;
  // binary and assign &=
  "op_binaryAndAssign", // = 25;
  // binary Xor assign ^=
  "op_binaryXorAssign", // = 26;
  // binary Or assign |=
  "op_binaryOrAssign", // = 27;
  // equals ==
  "op_equals", // = 28;
  // not equals !=
  "op_notequals", // = 29;
  // For c==, only. <code>op_pmdot</code> pointer-to-member field dereference.
  "op_pmdot", // = 30;
  // For c++, only. <code>op_pmarrow</code> pointer-to-member pointer dereference.
  "op_pmarrow", // = 31;
  // For g++, only. <code>op_max</code> represents >?
  "op_max", // = 32;
  // For g++, only. <code>op_min</code> represents <?
  "op_min", // = 33;
  // For gcc compilers, only. <code>op_ellipses</code> represents ... as used for case ranges.
  "op_ellipses" //= 34;
  )
  assert(operators2.length == 35)


  val type_ops = Array(
    // <code>op_sizeof</code> sizeof( typeId ) expression
    "op_sizeof", // = 0;
    // For c++, only.
    "op_typeid", // = 1;
    // For gnu-parsers, only.
    // <code>op_alignOf</code> is used for __alignOf( typeId ) type expressions.
    "op_alignof", // = 2;
    // For gnu-parsers, only.
    // <code>op_typeof</code> is used for typeof( typeId ) type expressions.
    "op_typeof" // = 3;
  )

  def evalType(node: IASTDeclSpecifier) = node match {
    case d: CASTSimpleDeclSpecifier     => types(d.getType)
    case d: CASTTypedefNameSpecifier    => d.getName
    case d: CASTElaboratedTypeSpecifier => d.getName // enough?
    case d: CASTCompositeTypeSpecifier  => d.getName // enough?
  }
  def evalExp(node: IASTNode): String = node match {
      case node: CASTLiteralExpression =>
          val lk = node.getKind
          node.toString
      case node: CASTIdExpression =>
          node.getName.toString
      case node: CASTUnaryExpression =>
          val op = operators1(node.getOperator)
          val arg = evalExp(node.getOperand)
          "("+op+" "+arg+")"
      case node: CASTBinaryExpression =>
          val op = operators2(node.getOperator)
          val arg1 = evalExp(node.getOperand1)
          val arg2 = evalExp(node.getOperand2)
          "("+op+" "+arg1+" "+arg2+")"
      case node: CASTFunctionCallExpression =>
          val fun = node.getFunctionNameExpression
          val arg = node.getParameterExpression
          evalExp(fun)+"("+evalExp(arg)+")"
      case node: CASTArraySubscriptExpression =>
          val a = node.getArrayExpression
          val x = node.getSubscriptExpression
          evalExp(a) + "["+ evalExp(x) + "]"
      case node: CASTExpressionList =>
          val as = node.getExpressions
          as.map(evalExp).mkString("(",",",")")
      case node: CASTInitializerList =>
          val as = node.getInitializers
          as.map(evalExp).mkString("{",",","}")
      case node: CASTEqualsInitializer => // copy initializer
          evalExp(node.getInitializerClause)
      case node: CASTCastExpression =>
          "("+types(node.getOperator)+")"+evalExp(node.getOperand)
      case node: CASTTypeIdExpression => // sizeof
          val op = type_ops(node.getOperator)
          val tp = node.getTypeId
          val declarator = tp.getAbstractDeclarator.asInstanceOf[CASTDeclarator]
          val declSpecifier = tp.getDeclSpecifier
          // TODO: pointer types, ...
          op + "("+evalType(declSpecifier)+")"
      case null => ""
      case _ => "(exp "+node+")"
  }
  def evalDeclarator(node: IASTDeclarator): Unit = node match {
    case d1: CASTFunctionDeclarator =>
      val ptr = d1.getPointerOperators()
      // TODO: pointer types, ...
      print(d1.getName)
      // parameters
      val xs = d1.getParameters();
      print("(")
      var first = true
      for (d <- xs) {
        if (!first) print(",")        
        print(evalType(d.getDeclSpecifier))
        print(" ")
        evalDeclarator(d.getDeclarator)
        first = false
      }
      print(")")
    case d1: CASTDeclarator =>
      val ptr = d1.getPointerOperators()
      // TODO: pointer types, ...
      print(d1.getName)
      /* val nested = d1.getNestedDeclarator // used at all?
      if (nested != null) {
        print("(")
        evalDeclarator(nested)
        print(")")
      }*/
      val init = d1.getInitializer
      if (init != null) {
        val init1 = init.asInstanceOf[CASTEqualsInitializer].getInitializerClause
        print(" = " + evalExp(init1))
      }
  }
  def evalLocalDecl(node: IASTDeclaration): Unit = node match {
      case node: CASTSimpleDeclaration =>
          val declSpecifier = node.getDeclSpecifier
          val decls = node.getDeclarators
          print(evalType(declSpecifier))
          print(" ")
          for (d <- decls) {
            evalDeclarator(d)
          }
          println
      case _ => println("dec "+node)
  }
  def evalStm(node: IASTStatement): Unit = node match {
      case node: CASTCompoundStatement =>
          println("{")
          for (s <- node.getStatements) evalStm(s)
          println("}")
      case node: CASTDeclarationStatement =>
          val decl = node.getDeclaration
          evalLocalDecl(decl)
      case node: CASTExpressionStatement =>
          val exp = node.getExpression
          println(evalExp(exp))
      case node: CASTWhileStatement =>
          val c = node.getCondition
          val b = node.getBody
          println("while "+evalExp(c))
          evalStm(b)
      case node: CASTDoStatement =>
          val c = node.getCondition
          val b = node.getBody
          print("do ")
          evalStm(b)      
          println("while "+evalExp(c))
      case node: CASTForStatement =>
          val c = node.getConditionExpression
          val i = node.getInitializerStatement
          val p = node.getIterationExpression
          val b = node.getBody
          print("for (")
          evalStm(i)
          print(";" + evalExp(c) + ";" + evalExp(p)+")")
          evalStm(b)

      case node: CASTIfStatement =>
          val c = node.getConditionExpression
          val a = node.getThenClause
          val b = node.getElseClause
          print("if "+evalExp(c)+" ")
          evalStm(a)
          print("else ")
          evalStm(b)
      case node: CASTSwitchStatement =>
          val c = node.getControllerExpression()
          val b = node.getBody
          println("switch "+evalExp(c))
          evalStm(b)
      case node: CASTCaseStatement =>
          println("case "+evalExp(node.getExpression)+":")
          //evalStm(node.getNestedStatement)
      case node: CASTLabelStatement =>
          println(node.getName + ":")
          evalStm(node.getNestedStatement)
      case node: CASTGotoStatement =>
          println("goto "+node.getName)
      case node: CASTBreakStatement =>
          println("break")
      case node: CASTContinueStatement =>
          println("continue")
      case node: CASTReturnStatement =>
          println("return "+evalExp(node.getReturnValue))
      case node: CASTNullStatement =>
          println("{}")
      case null => 
          println("{}")
      case _ => println("stm "+node)
  }
  def evalGlobalDecl(node: IASTDeclaration): Unit = node match {
      case node: CASTFunctionDefinition =>
          val declarator = node.getDeclarator()
          val declSpecifier = node.getDeclSpecifier
          print(evalType(declSpecifier))
          print(" ")
          evalDeclarator(declarator)
          evalStm(node.getBody)
      case _ =>
          evalLocalDecl(node)
  }
  def evalUnit(node: IASTTranslationUnit): Unit = node match {
      case node: CASTTranslationUnit =>
          val decls = node.getDeclarations
          for (d <- decls) evalGlobalDecl(d)
  }

  // CFG gen
  def resetCFG() = {
    nBlocks = 0
  }

  abstract class Continuation
  case class Return(e: IASTExpression) extends Continuation
  case class Jump(target: String) extends Continuation
  case class CJump(c: IASTExpression, a: String, b: String) extends Continuation

  case class Block(label: String, stms: Array[IASTStatement], cnt: Continuation) {
    val successors = cnt match {
      case Return(_)    => Nil
      case Jump(b)      => List(b)
      case CJump(c,a,b) => List(a,b)
    }
  }

  import scala.collection.{mutable,immutable}
  import mutable.ArrayBuffer

  
  // state per function
  var curFunction: String = null
  var blocks: ArrayBuffer[Block] = new ArrayBuffer
  var blockIndex: mutable.HashMap[String,Block] = new mutable.HashMap

  // state per block
  var blockName: String = null
  var stms: ArrayBuffer[IASTStatement] = new ArrayBuffer


  var nBlocks = 0
  def freshLabel(s:String="") = try "b"+s+nBlocks finally nBlocks += 1

  def endBlock(cnt: Continuation) = {
    val b = Block(blockName,stms.toArray, cnt)
    blocks += b
    blockIndex(blockName) = b
    stms.clear
    blockName = null
  }
  def startBlock(s: String) = {
    blockName = s
    stms.clear
  }

  def postDominators(blocks: List[Block]) = {
    var PostDom: Map[String,Set[String]] = Map()
    val labels = blocks.map(_.label).toSet
    val (exit,internal) = blocks.partition(_.successors.isEmpty)
    for (n <- exit)
      PostDom += (n.label -> Set(n.label))
    for (n <- internal)
      PostDom += (n.label -> labels)
    var p0 = PostDom
    do {
      p0 = PostDom
      for (n <- internal) {
        val x = (labels /: n.successors) ((a:Set[String],b:String) => a intersect PostDom(b))
        PostDom += (n.label -> (Set(n.label) ++ x))
      }
    } while (PostDom != p0)
    PostDom
  }







  def evalCfgStm(node: IASTStatement): Unit = node match {
      case node: CASTCompoundStatement =>
          // TODO: push / pop scope
          //println("{")
          for (s <- node.getStatements) evalCfgStm(s)
          //println("}")
      case node: CASTWhileStatement =>
          val c = node.getCondition
          val b = node.getBody
          val clabel = freshLabel("WhileHead")
          val blabel = freshLabel("WhileBody")
          val elabel = freshLabel("WhileExit")
          endBlock(Jump(clabel))
          startBlock(clabel)
          endBlock(CJump(c,blabel,elabel))
          startBlock(blabel)
          evalCfgStm(b)
          endBlock(Jump(clabel))
          startBlock(elabel)
      /*case node: CASTDoStatement =>
          val c = node.getCondition
          val b = node.getBody
          print("do ")
          evalCfgStm(b)      
          println("while "+evalExp(c))*/
      case node: CASTForStatement =>
          val c = node.getConditionExpression
          val i = node.getInitializerStatement
          val p = node.getIterationExpression
          val b = node.getBody
          val clabel = freshLabel("ForHead")
          val blabel = freshLabel("ForBody")
          val elabel = freshLabel("ForExit")
          evalCfgStm(i)
          endBlock(Jump(clabel))
          startBlock(clabel)
          endBlock(CJump(c,blabel,elabel))
          startBlock(blabel)
          evalCfgStm(b)
          if (p != null) evalCfgStm(new CASTExpressionStatement(p.copy))
          endBlock(Jump(clabel))
          startBlock(elabel)

      case node: CASTIfStatement =>
          val c = node.getConditionExpression
          val a = node.getThenClause
          val b = node.getElseClause
          val alabel = freshLabel("IfThen")
          val blabel = freshLabel("IfElse")
          val elabel = freshLabel("IfExit")
          endBlock(CJump(c,alabel,blabel))
          startBlock(alabel)
          evalCfgStm(a)
          endBlock(Jump(elabel))
          startBlock(blabel)
          evalCfgStm(b)
          endBlock(Jump(elabel))
          startBlock(elabel)
/*      case node: CASTSwitchStatement =>
          val c = node.getControllerExpression()
          val b = node.getBody
          println("switch "+evalExp(c))
          evalCfgStm(b)
      case node: CASTCaseStatement =>
          println("case "+evalExp(node.getExpression)+":")
*/
      case node: CASTLabelStatement =>
          endBlock(Jump(node.getName.toString))
          startBlock(node.getName.toString)
          evalCfgStm(node.getNestedStatement)
      case node: CASTGotoStatement =>
          endBlock(Jump(node.getName.toString))
          startBlock(freshLabel())
/*      case node: CASTBreakStatement =>
          println("break / TODO")
      case node: CASTContinueStatement =>
          println("continue / TODO")
*/          
      case node: CASTReturnStatement =>
          endBlock(Return(node.getReturnValue))
          startBlock(freshLabel())
      case node: CASTNullStatement =>
      case null => 
      case _ => stms += node
  }
  def evalCfgGlobalDecl(node: IASTDeclaration): Unit = node match {
      case node: CASTFunctionDefinition =>
          nBlocks = 0
          blocks.clear
          blockIndex.clear
          curFunction = node.getDeclarator.getName.toString

          val declarator = node.getDeclarator()
          val declSpecifier = node.getDeclSpecifier
          print(evalType(declSpecifier))
          print(" ")
          evalDeclarator(declarator)

          if (curFunction != "main")
            return println("{ ... skipping body ... }")

          println(" {")
          println("# control-flow graph:")

          val entryLabel = freshLabel("Entry")
          startBlock(entryLabel)
          evalCfgStm(node.getBody)
          endBlock(Return(null))

          for (v <- blocks) {
            println(v.label+": {")
            v.stms.foreach(evalStm)
            println(v.cnt)
            println("}")
          }

          if (blocks.length > 200) {
            println(s"CFG too large: ${blocks.length} nodes")
          } else {
            println("# loop headers:")

            val loopHeaders = new mutable.HashSet[String]

            var fuel = 20000
            var seen = new mutable.HashSet[String]
            def dfs(l: String, path: Set[String]): Unit = {
              fuel -= 1; if (fuel == 0) throw new Exception("XXX dfs out of fuel")              
              if (path contains l) {
                loopHeaders += l
              } else if (!seen(l)) {
                seen += l
                val path1 = path + l
                blockIndex(l).successors.foreach(l1 => dfs(l1, path1))
              }
            }
            time {
              dfs(entryLabel, Set.empty)
              println(loopHeaders)
            }

            println("# post-dominators:")
            val postDom = time {
              val postDom = postDominators(blocks.toList)
              println(postDom)
              postDom
            }

            println("# restructure:")

            fuel = 500000
            def consume(l: String, stop: Set[String], cont: Set[String]): Unit = {
              fuel -= 1; if (fuel == 0) throw new Exception("XXX consume out of fuel")
              
              if (stop contains l) {
                //println("// break "+l)
                return
              }
              if (cont contains l) {
                //println("// continue "+l)
                println(s"${l}_more = true")
                return
              }

              //println("// "+l)
              val b = blockIndex(l)

              // strict post-dominators (without self)
              val sdom = postDom(l)-l
              // immediate post-dominator (there can be at most one)
              var idom = sdom.filter(n => sdom.forall(postDom(n)))
              // Currently there's an issue in 
              // loop-invgen/string_concat-noarr_true-unreach-call_true-termination.i
              // TODO: use Cooper's algorithm to compute idom directly
              if (idom.contains("STUCK")) idom = Set("STUCK") // HACK
              if (l == "STUCK") idom = Set() // HACK
              assert(idom.size <= 1, s"sdom($l) = $sdom\nidom($l) = ${idom}")

              // Simplifying assumption: the immediate post-dominator
              // of a loop header is *outside* the loop. This is not
              // necessarily true in general.
              // TODO: refine to compute post-dom outside loop.
              val isLoop = loopHeaders contains l
              if (isLoop) {
                println("do {")
                println(s"bool ${l}_more = false")
              }
              val stop1 = idom
              val cont1 = if (isLoop) cont + l else cont

              b.stms.foreach(evalStm)
              b.cnt match {
                case Return(e) => 
                  println("return "+evalExp(e))
                  assert(idom.isEmpty)
                case Jump(a) => 
                  assert(a == l || idom == Set(a)) // handled below
                case CJump(c,a,b) => 
                  println("if "+evalExp(c)+" {")
                  consume(a, stop1, cont1)
                  println("} else {")
                  consume(b, stop1, cont1)
                  println("}")
              }
              if (isLoop)
                println(s"} while (${l}_more)")

              if (idom.nonEmpty)
                consume(idom.head, stop, cont)
            }
            time{consume(entryLabel, Set.empty, Set.empty)}
          }

          println("}")
      case _ =>
          evalLocalDecl(node)
  }
  def evalCfgUnit(node: IASTTranslationUnit): Unit = node match {
      case node: CASTTranslationUnit =>
          val decls = node.getDeclarations
          for (d <- decls) evalCfgGlobalDecl(d)
  }

}