package org.meta_environment.rascal.interpreter.matching;

import java.util.Iterator;
import java.util.Stack;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.IMap;
import org.eclipse.imp.pdb.facts.INode;
import org.eclipse.imp.pdb.facts.ISet;
import org.eclipse.imp.pdb.facts.ITuple;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.type.Type;
import org.meta_environment.rascal.interpreter.asserts.ImplementationError;
import org.meta_environment.rascal.interpreter.types.NonTerminalType;
import org.meta_environment.rascal.interpreter.types.RascalTypeFactory;
import org.meta_environment.uptr.SymbolAdapter;

public class NodeReader implements Iterator<IValue> {

	Stack<Object> spine = new Stack<Object>();
	
	private boolean bottomup;
	private boolean debug = false;
	
	NodeReader(INode node, boolean bottomup){
		this.bottomup = bottomup;
		initSpine(node);
	}
	
	private void initSpine(INode node){
		if(debug)System.err.println("initSpine: " + node.getType() + ", " + node);
		
		if(node.getType() instanceof NonTerminalType ||
		   node.getType().getName().equals("Tree")){
			pushConcreteSyntaxNode((IConstructor) node);
		} else {
			if(bottomup) {
				spine.push(node);
			}
			spine.push(node.getChildren().iterator());
			if(!bottomup) {
				spine.push(node);
			}
		}
	}
	
	private void pushConcreteSyntaxNode(IConstructor tree){
		if(debug)System.err.println("pushConcreteSyntaxNode: " + tree);
		String name = tree.getName();
		
		if(name.equals("sort") || name.equals("lit") || 
		   name.equals("char") || name.equals("single")){
			/*
			 * Don't recurse
			 */
			spine.push(tree);
			return;
		}
		
		if(name.equals("amb")){
			throw new ImplementationError("Cannot handle ambiguous subject");
		}
			
		NonTerminalType ctype = (NonTerminalType) RascalTypeFactory.getInstance().nonTerminalType(tree);
		if(debug)System.err.println("ctype.getSymbol=" + ctype.getSymbol());
		IConstructor sym = ctype.getSymbol();
        if(SymbolAdapter.isAnyList(sym)){
        	sym = SymbolAdapter.getSymbol(sym);
        	
        	int delta = 1;          // distance between "real" list elements, e.g. non-layout and non-separator
        	IList listElems = (IList) tree.get(1);
			if(SymbolAdapter.isIterPlus(sym) || SymbolAdapter.isIterStar(sym)){
				if(debug)System.err.println("pushConcreteSyntaxChildren: isIterPlus or isIterStar");
				delta = 2;
			} else if(SymbolAdapter.isIterPlusSep(sym) || SymbolAdapter.isIterStarSep(sym)){
				if(debug)System.err.println("pushConcreteSyntaxChildren: isIterPlusSep or isIterStarSep");
				delta = 4;
			}
			if(debug)
				for(int i = 0; i < listElems.length(); i++){
					System.err.println("#" + i + ": " + listElems.get(i));
				}
        	
			for(int i = listElems.length() - 1; i >= 0 ; i -= delta){
				if(debug)System.err.println("adding: " + listElems.get(i));
				pushConcreteSyntaxNode((IConstructor)listElems.get(i));
			}
		} else {
			if(debug)System.err.println("pushConcreteSyntaxNode: appl");
			/*
			 * appl(prod(...), [child0, layout0, child1, ...])
			 */
			if(bottomup) {
				spine.push(tree);
			}
			IList applArgs = (IList) tree.get(1);
			int delta = (SymbolAdapter.isLiteral(sym)) ? 1 : 2;   // distance between elements
			
			for(int i = applArgs.length() - 1; i >= 0 ; i -= delta){
				//spine.push(applArgs.get(i));
				pushConcreteSyntaxNode((IConstructor) applArgs.get(i));
			}
			if(!bottomup) {
				spine.push(tree);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public boolean hasNext() {
		while((spine.size() > 0) &&
			   (spine.peek() instanceof Iterator && !((Iterator<Object>) spine.peek()).hasNext())){
			spine.pop();
		}		
		return spine.size() > 0;
	}
	
	private IValue insertAndNext(IValue v, Iterator<IValue> children){
		if(bottomup){
			spine.push(v);
			spine.push(children);
		} else {
			spine.push(children);
			spine.push(v);
		}
		return next();
	}
	
	private IValue expand(IValue v){
		Type type = v.getType();
		if(type.isNodeType() || type.isConstructorType() || type.isAbstractDataType()){
			if(type.getName().equals("Tree")){
				pushConcreteSyntaxNode((IConstructor) v);
				return next();
			}
			
			return insertAndNext(v,  ((INode) v).getChildren().iterator());
		}
		if(type.isListType()){
			return insertAndNext(v, ((IList) v).iterator());
		}
		if(type.isSetType()){
			return insertAndNext(v, ((ISet) v).iterator());
		}
		if(type.isMapType()){
			return insertAndNext(v, ((IMap) v).iterator());
		}
		if(type.isTupleType()){
			ITuple tp = (ITuple) v;
			int arity = tp.arity();
			if(bottomup){
				spine.push(tp);
			}
			for(int i = arity - 1; i >= 0; i--){
				spine.push(tp.get(i));
			}
			if(!bottomup){
				spine.push(tp);
			}
			return next();
		}
		
		return v;
	}

	@SuppressWarnings("unchecked")
	public IValue next() {
		if(spine.peek() instanceof Iterator){
			Iterator<Object> iter = (Iterator<Object>) spine.peek();
			if(!iter.hasNext()){
				spine.pop();
				return next();
			}
			return expand((IValue) iter.next());
		}
		
		return (IValue) spine.pop();
	}

	public void remove() {
		throw new UnsupportedOperationException("remove from INodeReader");
	}
}
