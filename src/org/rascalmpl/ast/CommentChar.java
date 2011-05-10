
package org.rascalmpl.ast;


import org.eclipse.imp.pdb.facts.IConstructor;

import org.eclipse.imp.pdb.facts.IConstructor;

import org.eclipse.imp.pdb.facts.IValue;

import org.rascalmpl.interpreter.Evaluator;

import org.rascalmpl.interpreter.asserts.Ambiguous;

import org.rascalmpl.interpreter.env.Environment;

import org.rascalmpl.interpreter.matching.IBooleanResult;

import org.rascalmpl.interpreter.matching.IMatchingResult;

import org.rascalmpl.interpreter.result.Result;


public abstract class CommentChar extends AbstractAST {
  public CommentChar(IConstructor node) {
    super(node);
  }
  


static public class Ambiguity extends CommentChar {
  private final java.util.List<org.rascalmpl.ast.CommentChar> alternatives;

  public Ambiguity(IConstructor node, java.util.List<org.rascalmpl.ast.CommentChar> alternatives) {
    super(node);
    this.alternatives = java.util.Collections.unmodifiableList(alternatives);
  }

  @Override
  public Result<IValue> interpret(Evaluator __eval) {
    throw new Ambiguous((IConstructor) this.getTree());
  }
  
  @Override
  public org.eclipse.imp.pdb.facts.type.Type typeOf(Environment env) {
    throw new Ambiguous((IConstructor) this.getTree());
  }
  
  public java.util.List<org.rascalmpl.ast.CommentChar> getAlternatives() {
   return alternatives;
  }

  public <T> T accept(IASTVisitor<T> v) {
	return v.visitCommentCharAmbiguity(this);
  }
}



 
static public class Lexical extends CommentChar {
  private final java.lang.String string;
  public Lexical(IConstructor node, java.lang.String string) {
    super(node);
    this.string = string;
  }
  public java.lang.String getString() {
    return string;
  }
  public java.lang.String toString() {
    return string;
  }
  public <T> T accept(IASTVisitor<T> v) {
    return v.visitCommentCharLexical(this);
  }
}





}
